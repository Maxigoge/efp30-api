package ar.edu.efp30.api.admin;

import ar.edu.efp30.api.db.DatabaseIds;
import ar.edu.efp30.api.publicapi.PublicApiException;
import ar.edu.efp30.api.publicapi.PublicContentDtos.Page;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static ar.edu.efp30.api.admin.AdminMediaDtos.*;

@Service
public class AdminMediaService {
    private static final long IMAGE_LIMIT=5L*1024*1024;
    private static final long PDF_LIMIT=10L*1024*1024;
    private final JdbcTemplate jdbc;
    private final MediaStorageService storage;

    public AdminMediaService(JdbcTemplate jdbc,MediaStorageService storage) { this.jdbc=jdbc; this.storage=storage; }

    public Page<MediaAdmin> list(int page,int size,String visibility,Boolean enabled) {
        if(page<0) throw PublicApiException.badRequest("page","Debe ser mayor o igual a cero.");
        if(size<1||size>100) throw PublicApiException.badRequest("size","Debe estar entre 1 y 100.");
        if(visibility!=null&&!List.of("PUBLIC","PRIVATE").contains(visibility)) throw PublicApiException.badRequest("visibility","La visibilidad no es válida.");
        StringBuilder where=new StringBuilder(" WHERE 1=1"); List<Object> args=new ArrayList<>();
        if(visibility!=null){where.append(" AND visibility=?");args.add(visibility);}
        if(enabled!=null){where.append(" AND enabled=?");args.add(enabled);}
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM media"+where,Long.class,args.toArray());
        List<Object> queryArgs=new ArrayList<>(args);queryArgs.add(size);queryArgs.add((long)page*size);
        List<MediaAdmin> items=jdbc.query("SELECT id,original_name,mime_type,size_bytes,visibility,enabled,version FROM media"+where+
                " ORDER BY created_at DESC,id ASC LIMIT ? OFFSET ?",(rs,row)->media(rs),queryArgs.toArray());
        return new Page<>(items,page,size,total,total==0?0:(total+size-1)/size);
    }

    public MediaAdmin get(UUID id) { return one(id).metadata(); }

    @Transactional
    public MediaAdmin upload(MultipartFile file,String visibility) {
        if(file==null||file.isEmpty()) throw PublicApiException.badRequest("file","El archivo es obligatorio y no puede estar vacío.");
        if(!List.of("PUBLIC","PRIVATE").contains(visibility)) throw PublicApiException.badRequest("visibility","La visibilidad debe ser PUBLIC o PRIVATE.");
        String originalName=file.getOriginalFilename();
        if(originalName==null||originalName.isBlank()||originalName.length()>255||originalName.contains("/")||originalName.contains("\\")
                ||originalName.contains("..")||originalName.contains("\r")||originalName.contains("\n"))
            throw PublicApiException.badRequest("file","El nombre del archivo no puede incluir rutas ni controles.");
        if(file.getSize()>PDF_LIMIT) throw PublicApiException.payloadTooLarge();
        byte[] bytes;
        try { bytes=file.getBytes(); } catch(IOException exception) { throw PublicApiException.badRequest("file","No se pudo leer el archivo."); }
        Detected detected=detect(bytes);
        String extension=extension(originalName);
        if(!detected.extension().equals(extension)||!detected.mimeType().equalsIgnoreCase(file.getContentType()))
            throw PublicApiException.unsupportedMediaType();
        long limit=detected.mimeType().equals("application/pdf")?PDF_LIMIT:IMAGE_LIMIT;
        if(bytes.length>limit) throw PublicApiException.payloadTooLarge();
        UUID id=UUID.randomUUID(); String key=id+"."+detected.extension();
        String sha=sha256(bytes);
        try {
            storage.store(id,detected.extension(),bytes);
            jdbc.update("INSERT INTO media (id,object_key,original_name,mime_type,size_bytes,sha256,visibility,enabled,version) "
                            + "VALUES (?,?,?,?,?,?,?,TRUE,0)",id,key,originalName,detected.mimeType(),(long)bytes.length,sha,visibility);
        } catch(RuntimeException|IOException exception) {
            storage.delete(key);
            if(exception instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("No se pudo guardar el archivo.",exception);
        }
        return get(id);
    }

    @Transactional
    public MediaAdmin update(UUID id,MediaUpdateRequest request) {
        StoredMedia current=one(id);
        if(current.metadata().version()!=request.version()) throw versionConflict();
        if("PRIVATE".equals(request.visibility())&&"PUBLIC".equals(current.metadata().visibility())&&referenceCount(id)>0)
            throw PublicApiException.conflict("MEDIA_IN_PUBLIC_USE","No se puede volver privado un medio con referencias públicas.");
        int changed=jdbc.update("UPDATE media SET enabled=?,visibility=?,version=version+1 WHERE id=? AND version=?",
                request.enabled(),request.visibility(),id,request.version());
        if(changed!=1) throw versionConflict();
        return get(id);
    }

    public StoredMedia privateContent(UUID id) {
        StoredMedia value=one(id);
        if(!"PRIVATE".equals(value.metadata().visibility())||!value.metadata().enabled()) throw PublicApiException.notFound();
        return new StoredMedia(value.metadata(),value.objectKey(),storage.resolve(value.objectKey(),value.metadata().sizeBytes()));
    }

    private long referenceCount(UUID id) {
        Long count=jdbc.queryForObject("SELECT (SELECT COUNT(*) FROM news WHERE cover_media_id=?) + "
                + "(SELECT COUNT(*) FROM course WHERE cover_media_id=?) + "
                + "(SELECT COUNT(*) FROM course_photo WHERE media_id=?)",Long.class,id,id,id);
        return count==null?0:count;
    }

    private StoredMedia one(UUID id) {
        List<StoredMedia> rows=jdbc.query("SELECT id,object_key,original_name,mime_type,size_bytes,visibility,enabled,version FROM media WHERE id=?",
                (rs,row)->new StoredMedia(media(rs),rs.getString("object_key"),null),id);
        if(rows.isEmpty()) throw PublicApiException.notFound();
        return rows.getFirst();
    }

    private MediaAdmin media(ResultSet rs) throws SQLException {
        UUID id=DatabaseIds.read(rs, "id");String visibility=rs.getString("visibility");boolean enabled=rs.getBoolean("enabled");
        return new MediaAdmin(id,rs.getString("original_name"),rs.getString("mime_type"),rs.getLong("size_bytes"),
                visibility,enabled,"PUBLIC".equals(visibility)&&enabled?"/api/v1/public/media/"+id:null,rs.getLong("version"));
    }

    private Detected detect(byte[] bytes) {
        if(bytes.length>=3&&(bytes[0]&255)==0xff&&(bytes[1]&255)==0xd8&&(bytes[2]&255)==0xff) return new Detected("image/jpeg","jpg");
        byte[] png={(byte)0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a};
        if(starts(bytes,png)) return new Detected("image/png","png");
        if(bytes.length>=12&&ascii(bytes,0,4).equals("RIFF")&&ascii(bytes,8,4).equals("WEBP")) return new Detected("image/webp","webp");
        if(bytes.length>=5&&ascii(bytes,0,5).equals("%PDF-")) return new Detected("application/pdf","pdf");
        throw PublicApiException.unsupportedMediaType();
    }
    private boolean starts(byte[] value,byte[] prefix) {
        if(value.length<prefix.length)return false;for(int i=0;i<prefix.length;i++)if(value[i]!=prefix[i])return false;return true;
    }
    private String ascii(byte[] value,int offset,int length) { return new String(value,offset,length,java.nio.charset.StandardCharsets.US_ASCII); }
    private String extension(String filename) {
        int dot=filename.lastIndexOf('.');if(dot<0||dot==filename.length()-1)throw PublicApiException.unsupportedMediaType();
        String ext=filename.substring(dot+1).toLowerCase(Locale.ROOT);
        return switch(ext){case "jpg","jpeg"->"jpg";case "png"->"png";case "webp"->"webp";case "pdf"->"pdf";default->throw PublicApiException.unsupportedMediaType();};
    }
    private String sha256(byte[] bytes) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(NoSuchAlgorithmException exception){throw new IllegalStateException("SHA-256 no está disponible.",exception);}
    }
    private PublicApiException versionConflict() { return PublicApiException.conflict("VERSION_CONFLICT","El recurso cambió desde que lo consultaste. Recargalo antes de guardar."); }
    private record Detected(String mimeType,String extension) { }
    public record StoredMedia(MediaAdmin metadata,String objectKey,java.nio.file.Path path) { }
}


