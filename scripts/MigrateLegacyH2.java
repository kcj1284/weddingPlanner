import java.sql.*;
import java.util.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;

/** Offline import into an empty, migrated PostgreSQL schema; source is read-only. */
class MigrateLegacyH2 {
    static final Map<String,String> TABLES=new LinkedHashMap<>();
    static {
        TABLES.put("app_user","id");TABLES.put("meeting","id");TABLES.put("member","meeting_id,user_id");
        TABLES.put("poll_option","id");TABLES.put("vote","meeting_id,user_id,option_id");
        TABLES.put("account_kind","user_id");TABLES.put("social_identity","provider,subject");TABLES.put("guest_link","id");
    }
    static Calendar koreanCalendar(){return Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"));}
    static String digest(Connection connection,String table,String order) throws Exception {
        var hash=MessageDigest.getInstance("SHA-256");
        try(var statement=connection.createStatement();var rows=statement.executeQuery("SELECT * FROM "+table+" ORDER BY "+order)) {
            while(rows.next())for(int i=1;i<=rows.getMetaData().getColumnCount();i++) {
                Object value=rows.getObject(i);
                if(value!=null&&rows.getMetaData().getColumnName(i).toLowerCase(Locale.ROOT).endsWith("_at"))value=rows.getTimestamp(i,koreanCalendar()).toInstant().toString();
                if(value instanceof Number)value=new java.math.BigDecimal(value.toString()).stripTrailingZeros().toPlainString();
                String encoded=value==null?"N;":"V"+value.toString().getBytes(StandardCharsets.UTF_8).length+":"+value+";";
                hash.update(encoded.getBytes(StandardCharsets.UTF_8));
            }
        }
        return HexFormat.of().formatHex(hash.digest());
    }
    public static void main(String[] args) throws Exception {
        Class.forName("org.h2.Driver");Class.forName("org.postgresql.Driver");
        try(var source=DriverManager.getConnection("jdbc:h2:file:./data/cheongmo;MODE=Oracle;ACCESS_MODE_DATA=r;IFEXISTS=TRUE","sa","");
            var target=DriverManager.getConnection(System.getenv("DATABASE_URL"),System.getenv("DATABASE_USERNAME"),System.getenv("DATABASE_PASSWORD"))) {
            source.setReadOnly(true);target.setAutoCommit(false);
            try {
                for(var table:TABLES.keySet())try(var statement=target.createStatement();var count=statement.executeQuery("SELECT COUNT(*) FROM "+table)) {
                    count.next();if(count.getLong(1)!=0)throw new IllegalStateException("대상 PostgreSQL DB가 비어 있지 않습니다. 이관을 중단합니다.");
                }
                for(var entry:TABLES.entrySet()) {
                    String table=entry.getKey();
                    try(var existence=source.prepareStatement("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='PUBLIC' AND table_name=?")) {
                        existence.setString(1,table.toUpperCase(Locale.ROOT));
                        try(var found=existence.executeQuery()){found.next();if(found.getInt(1)==0)continue;}
                    }
                    int count=0;
                    try(var statement=source.createStatement();var rows=statement.executeQuery("SELECT * FROM "+table)) {
                        int size=rows.getMetaData().getColumnCount();var columns=new ArrayList<String>();
                        for(int i=1;i<=size;i++)columns.add(rows.getMetaData().getColumnName(i).toLowerCase(Locale.ROOT));
                        try(var insert=target.prepareStatement("INSERT INTO "+table+"("+String.join(",",columns)+") VALUES("+String.join(",",Collections.nCopies(size,"?"))+")")) {
                            while(rows.next()) {
                                for(int i=1;i<=size;i++) {
                                    Object value=rows.getObject(i);
                                    if(value!=null&&columns.get(i-1).endsWith("_at"))value=rows.getTimestamp(i,koreanCalendar()).toInstant().atOffset(ZoneOffset.UTC);
                                    insert.setObject(i,value);
                                }
                                insert.executeUpdate();count++;
                            }
                        }
                    }
                    if(!digest(source,table,entry.getValue()).equals(digest(target,table,entry.getValue())))throw new IllegalStateException(table+" 내용 검증이 일치하지 않습니다.");
                    System.out.println(table+": "+count+" rows, content verification PASS");
                }
                target.commit();System.out.println("Migration committed. Original H2 file retained.");
            }catch(Exception failure){target.rollback();throw failure;}
        }
    }
}
