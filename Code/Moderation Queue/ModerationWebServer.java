import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.*;

public class ModerationWebServer {
    static final List<Post> posts = new ArrayList<>();
    static final List<Decision> log = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        posts.add(new Post(1, "Alice", "Looking for advice about my resume.", "Reported as inappropriate"));
        posts.add(new Post(2, "Bob", "This is a demo post that may violate guidelines.", "Reported by a user"));
        posts.add(new Post(3, "Charlie", "This post has not been flagged.", ""));
        posts.get(0).flagged = true;
        posts.get(1).flagged = true;

        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", 8080), 0);
        server.createContext("/", ModerationWebServer::handle);
        server.start();
        System.out.println("Moderation webpage: http://localhost:8080");
        System.out.println("Keep this terminal running. Press Ctrl+C to stop.");
    }

    static void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath(), method = ex.getRequestMethod();
        try {
            if (method.equals("GET") && path.equals("/")) {
                String html = loadIndexHtml();
                send(ex, 200, "text/html; charset=utf-8", html);
            } else if (method.equals("GET") && path.equals("/api/moderation/queue")) {
                StringBuilder b = new StringBuilder("[");
                boolean first = true;
                for (Post p : posts) if (p.flagged && !p.removed) {
                    if (!first) b.append(","); first = false; b.append(postJson(p));
                }
                send(ex, 200, "application/json", b.append("]").toString());
            } else if (method.equals("GET") && path.equals("/api/posts")) {
                StringBuilder b = new StringBuilder("[");
                for (int i=0;i<posts.size();i++) { if(i>0)b.append(","); b.append(postJson(posts.get(i))); }
                send(ex, 200, "application/json", b.append("]").toString());
            } else if (method.equals("GET") && path.equals("/api/moderation/log")) {
                StringBuilder b = new StringBuilder("[");
                for (int i=0;i<log.size();i++) {
                    Decision d=log.get(i); if(i>0)b.append(",");
                    b.append("{\"postId\":").append(d.id).append(",\"action\":\"").append(esc(d.action))
                     .append("\",\"moderator\":\"").append(esc(d.moderator)).append("\",\"reason\":\"")
                     .append(esc(d.reason)).append("\",\"timestamp\":\"").append(esc(d.time)).append("\"}");
                }
                send(ex, 200, "application/json", b.append("]").toString());
            } else if (method.equals("POST") && path.matches("/api/posts/\\d+/flag")) {
                int id = idFrom(path, "/api/posts/");
                Post p = find(id);
                if (p == null) throw new IllegalArgumentException("Post not found.");
                if (p.removed) throw new IllegalStateException("Removed posts cannot be flagged.");
                p.flagged=true;
                send(ex, 200, "application/json", "{\"message\":\"Post flagged.\"}");
            } else if (method.equals("POST") && path.matches("/api/moderation/\\d+/action")) {
                int id = idFrom(path, "/api/moderation/");
                String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String action = jsonValue(body, "action"), reason = jsonValue(body, "reason");
                String moderator = jsonValue(body, "moderator");
                if (reason == null || reason.trim().isEmpty()) throw new IllegalArgumentException("A brief reason is required.");
                if (!"DISMISS".equals(action) && !"REMOVE".equals(action)) throw new IllegalArgumentException("Action must be DISMISS or REMOVE.");
                Post p = find(id);
                if (p == null) throw new IllegalArgumentException("Post not found.");
                if (!p.flagged || p.removed) throw new IllegalStateException("Post is not in the active moderation queue.");
                if (moderator == null || moderator.trim().isEmpty()) moderator = "local-test-admin";
                if (action.equals("DISMISS")) p.flagged=false;
                else { p.flagged=false; p.removed=true; }
                log.add(new Decision(id, moderator, action, reason.trim(), LocalDateTime.now().toString()));
                send(ex, 200, "application/json", "{\"message\":\"Decision recorded.\"}");
            } else send(ex, 404, "application/json", "{\"error\":\"Endpoint not found.\"}");
        } catch (IllegalArgumentException | IllegalStateException e) {
            send(ex, 400, "application/json", "{\"error\":\""+esc(e.getMessage())+"\"}");
        } catch (Exception e) {
            send(ex, 500, "application/json", "{\"error\":\""+esc(e.getMessage())+"\"}");
        }
    }


    static String loadIndexHtml() throws IOException {
        // First try the classpath (works when resources are configured in the IDE/build).
        try (InputStream in = ModerationWebServer.class.getResourceAsStream("/static/index.html")) {
            if (in != null) return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        // Then try common project-folder locations. This also works when VS Code runs
        // the Java file directly without adding src/main/resources to the classpath.
        String[] candidates = {
            "src/main/resources/static/index.html",
            "moderation_queue_web/src/main/resources/static/index.html",
            "static/index.html"
        };
        for (String candidate : candidates) {
            File file = new File(candidate);
            if (file.isFile()) return java.nio.file.Files.readString(file.toPath(), StandardCharsets.UTF_8);
        }
        throw new IOException("Could not find index.html. Expected src/main/resources/static/index.html. "
            + "Open the moderation_queue_web project folder in VS Code and confirm the file exists.");
    }

    static int idFrom(String path, String prefix) { return Integer.parseInt(path.substring(prefix.length()).split("/")[0]); }
    static Post find(int id) { for(Post p:posts) if(p.id==id)return p; return null; }
    static String jsonValue(String body, String key) {
        Matcher m=Pattern.compile("\""+Pattern.quote(key)+"\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").matcher(body);
        if(!m.find())return null;
        return m.group(1).replace("\\n","\n").replace("\\r","\r").replace("\\\"","\"").replace("\\\\","\\");
    }
    static String postJson(Post p) {
        return "{\"id\":"+p.id+",\"author\":\""+esc(p.author)+"\",\"content\":\""+esc(p.content)
            +"\",\"flagReason\":\""+esc(p.flagReason)+"\",\"flagged\":"+p.flagged+",\"removed\":"+p.removed+"}";
    }
    static String esc(String s) { if(s==null)return ""; return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r").replace("\t","\\t"); }
    static void send(HttpExchange ex,int status,String type,String body)throws IOException{
        byte[] data=body.getBytes(StandardCharsets.UTF_8); ex.getResponseHeaders().set("Content-Type",type+"; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control","no-store"); ex.sendResponseHeaders(status,data.length);
        try(OutputStream out=ex.getResponseBody()){out.write(data);}
    }
    static class Post {
        int id; String author,content,flagReason; boolean flagged,removed;
        Post(int i,String a,String c,String f){id=i;author=a;content=c;flagReason=f;}
    }
    static class Decision {
        int id; String moderator,action,reason,time;
        Decision(int i,String m,String a,String r,String t){id=i;moderator=m;action=a;reason=r;time=t;}
    }
}
