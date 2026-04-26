import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.text.*;
import java.util.*;
import java.util.stream.*;

public class LibraryServer {

    // ── In-memory data ────────────────────────────────────────────
    static List<Book> books = new ArrayList<>();
    static List<User> users = new ArrayList<>();
    static List<Tx>   txs   = new ArrayList<>();
    static int txCount = 1;

    static class Book {
        String id, title, author, publisher, genre, checkedOutBy, dueDate, synopsis;
        boolean checkedOut;
        Book(String id, String title, String author, String publisher, String genre) {
            this.id=id; this.title=title; this.author=author;
            this.publisher=publisher; this.genre=genre;
            this.synopsis="A beloved classic in the "+genre+" genre.";
        }
    }
    static class User {
        String id, name, email, password, role;
        List<String> books = new ArrayList<>();
        User(String id,String name,String email,String password,String role){
            this.id=id;this.name=name;this.email=email;this.password=password;this.role=role;
        }
    }
    static class Tx {
        String id,userId,userName,bookId,bookTitle,type,date,returnDate;
        Tx(String id,String userId,String userName,String bookId,String bookTitle,String type,String date,String returnDate){
            this.id=id;this.userId=userId;this.userName=userName;
            this.bookId=bookId;this.bookTitle=bookTitle;this.type=type;
            this.date=date;this.returnDate=returnDate;
        }
    }

    // ── Seed data ────────────────────────────────────────────────
    static {
        users.add(new User("U001","Admin",        "admin@library.com","admin123","admin"));
        users.add(new User("U002","Alice Johnson","alice@email.com",  "alice123","user"));
        users.add(new User("U003","Bob Smith",    "bob@email.com",    "bob123",  "user"));
        users.add(new User("U004","Carol White",  "carol@email.com",  "carol123","user"));

        String[][] bd = {
            {"B001","The Great Gatsby","F. Scott Fitzgerald","Scribner","Fiction"},
            {"B002","To Kill a Mockingbird","Harper Lee","HarperCollins","Fiction"},
            {"B003","1984","George Orwell","Secker & Warburg","Dystopian"},
            {"B004","Pride and Prejudice","Jane Austen","T. Egerton","Romance"},
            {"B005","The Hobbit","J.R.R. Tolkien","Allen & Unwin","Fantasy"},
            {"B006","Dune","Frank Herbert","Chilton Books","Sci-Fi"},
            {"B007","Brave New World","Aldous Huxley","Chatto & Windus","Dystopian"},
            {"B008","The Catcher in the Rye","J.D. Salinger","Little Brown","Fiction"},
            {"B009","The Lord of the Rings","J.R.R. Tolkien","Allen & Unwin","Fantasy"},
            {"B010","The Da Vinci Code","Dan Brown","Doubleday","Mystery"},
            {"B011","Gone Girl","Gillian Flynn","Crown Publishing","Mystery"},
            {"B012","Frankenstein","Mary Shelley","Lackington Hughes","Horror"},
            {"B013","Dracula","Bram Stoker","Archibald Constable","Horror"},
            {"B014","Sapiens","Yuval Noah Harari","Harper Collins","History"},
            {"B015","A Brief History of Time","Stephen Hawking","Bantam Books","Science"},
            {"B016","The Diary of a Young Girl","Anne Frank","Contact Publishing","Biography"},
            {"B017","Steve Jobs","Walter Isaacson","Simon & Schuster","Biography"},
            {"B018","The Hitchhiker's Guide to the Galaxy","Douglas Adams","Pan Books","Sci-Fi"},
            {"B019","Ender's Game","Orson Scott Card","Tor Books","Sci-Fi"},
            {"B020","The Name of the Wind","Patrick Rothfuss","DAW Books","Fantasy"},
            {"B021","And Then There Were None","Agatha Christie","Collins Crime Club","Mystery"},
            {"B022","The Guns of August","Barbara Tuchman","Macmillan Publishers","History"},
            {"B023","The Alchemist","Paulo Coelho","HarperCollins","Fiction"},
            {"B024","Educated","Tara Westover","Random House","Biography"},
            {"B025","The Selfish Gene","Richard Dawkins","Oxford University Press","Science"},
        };
        for (String[] r : bd) books.add(new Book(r[0],r[1],r[2],r[3],r[4]));
    }

    // ── Helpers ──────────────────────────────────────────────────
    static String today() { return new SimpleDateFormat("yyyy-MM-dd").format(new Date()); }
    static String plus14() {
        Calendar c = Calendar.getInstance(); c.add(Calendar.DAY_OF_MONTH,14);
        return new SimpleDateFormat("yyyy-MM-dd").format(c.getTime());
    }
    static String nextTxId()   { return String.format("TX%04d", txCount++); }
    static User   findUser(String id)  { for(User u:users) if(u.id.equals(id)) return u; return null; }
    static User   findEmail(String e)  { for(User u:users) if(u.email.equalsIgnoreCase(e)) return u; return null; }
    static Book   findBook(String id)  { for(Book b:books) if(b.id.equals(id)) return b; return null; }
    static String nextUserId() {
        int max=0; for(User u:users){try{int n=Integer.parseInt(u.id.substring(1));if(n>max)max=n;}catch(Exception e){}} return String.format("U%03d",max+1);
    }
    static String nextBookId() {
        int max=0; for(Book b:books){try{int n=Integer.parseInt(b.id.substring(1));if(n>max)max=n;}catch(Exception e){}} return String.format("B%03d",max+1);
    }

    // ── JSON builders ────────────────────────────────────────────
    static String esc(String s){ return s==null?"":s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n"); }
    static String bookJson(Book b){
        return "{\"id\":\""+esc(b.id)+"\",\"title\":\""+esc(b.title)+"\",\"author\":\""+esc(b.author)+
               "\",\"publisher\":\""+esc(b.publisher)+"\",\"genre\":\""+esc(b.genre)+
               "\",\"checkedOut\":"+b.checkedOut+",\"checkedOutBy\":\""+esc(b.checkedOutBy)+
               "\",\"dueDate\":\""+esc(b.dueDate)+"\",\"synopsis\":\""+esc(b.synopsis)+"\"}";
    }
    static String userJson(User u){
        String bks = u.books.stream().map(s->"\""+esc(s)+"\"").collect(Collectors.joining(","));
        return "{\"id\":\""+esc(u.id)+"\",\"name\":\""+esc(u.name)+"\",\"email\":\""+esc(u.email)+
               "\",\"role\":\""+esc(u.role)+"\",\"checkedOutBooks\":["+bks+"]}";
    }
    static String txJson(Tx t){
        return "{\"id\":\""+esc(t.id)+"\",\"userId\":\""+esc(t.userId)+"\",\"userName\":\""+esc(t.userName)+
               "\",\"bookId\":\""+esc(t.bookId)+"\",\"bookTitle\":\""+esc(t.bookTitle)+
               "\",\"type\":\""+esc(t.type)+"\",\"date\":\""+esc(t.date)+"\",\"returnDate\":\""+esc(t.returnDate)+"\"}";
    }

    // ── Simple JSON parser (key:"value") ────────────────────────
    static String getJson(String body, String key){
        String search = "\""+key+"\":\"";
        int s = body.indexOf(search); if(s<0) return null;
        s += search.length(); int e=s;
        while(e<body.length()){ if(body.charAt(e)=='"' && body.charAt(e-1)!='\\') break; e++; }
        return body.substring(s,e);
    }

    // ── Response helpers ─────────────────────────────────────────
    static void send(HttpExchange ex, int code, String body) throws IOException {
        byte[] bytes = body.getBytes("UTF-8");
        ex.getResponseHeaders().set("Content-Type","application/json");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin","*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods","GET,POST,DELETE,OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers","Content-Type");
        ex.sendResponseHeaders(code, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.getResponseBody().close();
    }
    static void ok(HttpExchange ex, String json)  throws IOException { send(ex,200,json); }
    static void err(HttpExchange ex, String msg)  throws IOException { send(ex,400,"{\"error\":\""+esc(msg)+"\"}"); }
    static void unauth(HttpExchange ex)           throws IOException { send(ex,401,"{\"error\":\"Invalid credentials\"}"); }
    static String body(HttpExchange ex)           throws IOException { return new String(ex.getRequestBody().readAllBytes(),"UTF-8"); }

    // ── Main ──────────────────────────────────────────────────────
    public static void main(String[] args) throws Exception {
        int port = 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // Serve static files (index.html)
        server.createContext("/", ex -> {
            if(ex.getRequestMethod().equals("OPTIONS")){ send(ex,200,""); return; }
            String path = ex.getRequestURI().getPath();
            if(path.equals("/") || path.equals("/index.html")) path = "/index.html";
            File f = new File("." + path);
            if(!f.exists() || f.isDirectory()) f = new File("./index.html");
            if(!f.exists()){ send(ex,404,"\"Not found\""); return; }
            byte[] bytes = Files.readAllBytes(f.toPath());
            String ct = path.endsWith(".html")?"text/html":path.endsWith(".js")?"application/javascript":"text/plain";
            ex.getResponseHeaders().set("Content-Type", ct+"; charset=UTF-8");
            ex.getResponseHeaders().set("Access-Control-Allow-Origin","*");
            ex.sendResponseHeaders(200,bytes.length);
            ex.getResponseBody().write(bytes); ex.getResponseBody().close();
        });

        // POST /api/login
        server.createContext("/api/login", ex -> {
            if(ex.getRequestMethod().equals("OPTIONS")){ send(ex,200,""); return; }
            String b = body(ex);
            String email=getJson(b,"email"), pass=getJson(b,"password");
            User u = findEmail(email);
            if(u==null||!u.password.equals(pass)){ unauth(ex); return; }
            ok(ex, userJson(u));
        });

        // POST /api/register
        server.createContext("/api/register", ex -> {
            if(ex.getRequestMethod().equals("OPTIONS")){ send(ex,200,""); return; }
            String b = body(ex);
            String name=getJson(b,"name"), email=getJson(b,"email"), pass=getJson(b,"password");
            if(name==null||email==null||pass==null){ err(ex,"Missing fields"); return; }
            if(findEmail(email)!=null){ err(ex,"Email already registered"); return; }
            User u = new User(nextUserId(),name,email,pass,"user");
            users.add(u); ok(ex, userJson(u));
        });

        // GET /api/books
        server.createContext("/api/books", ex -> {
            if(ex.getRequestMethod().equals("OPTIONS")){ send(ex,200,""); return; }
            String method = ex.getRequestMethod();
            if(method.equals("GET")){
                String json = "["+books.stream().map(LibraryServer::bookJson).collect(Collectors.joining(","))+"]";
                ok(ex,json);
            } else if(method.equals("POST")){
                String b=body(ex);
                String title=getJson(b,"title"),author=getJson(b,"author"),
                       publisher=getJson(b,"publisher"),genre=getJson(b,"genre");
                if(title==null||author==null||genre==null){ err(ex,"Missing fields"); return; }
                Book bk = new Book(nextBookId(),title,author,publisher==null?"":publisher,genre);
                books.add(bk); ok(ex,"{\"id\":\""+bk.id+"\",\"message\":\"Book added\"}");
            } else { err(ex,"Method not allowed"); }
        });

        // DELETE /api/books/{id}
        server.createContext("/api/books/", ex -> {
            if(ex.getRequestMethod().equals("OPTIONS")){ send(ex,200,""); return; }
            if(!ex.getRequestMethod().equals("DELETE")){ err(ex,"Method not allowed"); return; }
            String id = ex.getRequestURI().getPath().replace("/api/books/","");
            Book bk = findBook(id);
            if(bk==null||bk.checkedOut){ err(ex,"Cannot delete (not found or checked out)"); return; }
            books.remove(bk); ok(ex,"{\"message\":\"Book deleted\"}");
        });

        // POST /api/checkout
        server.createContext("/api/checkout", ex -> {
            if(ex.getRequestMethod().equals("OPTIONS")){ send(ex,200,""); return; }
            String b=body(ex);
            String uid=getJson(b,"userId"), bid=getJson(b,"bookId");
            User u=findUser(uid); Book bk=findBook(bid);
            if(u==null){ err(ex,"User not found"); return; }
            if(bk==null){ err(ex,"Book not found"); return; }
            if(bk.checkedOut){ err(ex,"Book already checked out"); return; }
            if(u.books.size()>=5){ err(ex,"Checkout limit reached (max 5)"); return; }
            bk.checkedOut=true; bk.checkedOutBy=uid; bk.dueDate=plus14();
            u.books.add(bid);
            txs.add(new Tx(nextTxId(),uid,u.name,bid,bk.title,"CHECK-OUT",today(),bk.dueDate));
            ok(ex,"{\"message\":\"Checked out successfully\"}");
        });

        // POST /api/return
        server.createContext("/api/return", ex -> {
            if(ex.getRequestMethod().equals("OPTIONS")){ send(ex,200,""); return; }
            String b=body(ex);
            String uid=getJson(b,"userId"), bid=getJson(b,"bookId");
            User u=findUser(uid); Book bk=findBook(bid);
            if(u==null){ err(ex,"User not found"); return; }
            if(bk==null){ err(ex,"Book not found"); return; }
            if(!bk.checkedOut||!uid.equals(bk.checkedOutBy)){ err(ex,"Book not checked out by this user"); return; }
            bk.checkedOut=false; bk.checkedOutBy=null; bk.dueDate=null;
            u.books.remove(bid);
            txs.add(new Tx(nextTxId(),uid,u.name,bid,bk.title,"RETURN",today(),"-"));
            ok(ex,"{\"message\":\"Returned successfully\"}");
        });

        // GET /api/users
        server.createContext("/api/users", ex -> {
            if(ex.getRequestMethod().equals("OPTIONS")){ send(ex,200,""); return; }
            String method=ex.getRequestMethod();
            if(method.equals("GET")){
                String json="["+users.stream().map(LibraryServer::userJson).collect(Collectors.joining(","))+"]";
                ok(ex,json);
            } else { err(ex,"Method not allowed"); }
        });

        // DELETE /api/users/{id}
        server.createContext("/api/users/", ex -> {
            if(ex.getRequestMethod().equals("OPTIONS")){ send(ex,200,""); return; }
            if(!ex.getRequestMethod().equals("DELETE")){ err(ex,"Method not allowed"); return; }
            String id=ex.getRequestURI().getPath().replace("/api/users/","");
            User u=findUser(id);
            if(u==null||u.role.equals("admin")){ err(ex,"Cannot delete admin or user not found"); return; }
            users.remove(u); ok(ex,"{\"message\":\"User deleted\"}");
        });

        // GET /api/transactions
        server.createContext("/api/transactions", ex -> {
            if(ex.getRequestMethod().equals("OPTIONS")){ send(ex,200,""); return; }
            String json="["+txs.stream().map(LibraryServer::txJson).collect(Collectors.joining(","))+"]";
            ok(ex,json);
        });

        // GET /api/stats
        server.createContext("/api/stats", ex -> {
            if(ex.getRequestMethod().equals("OPTIONS")){ send(ex,200,""); return; }
            long co=books.stream().filter(bk->bk.checkedOut).count();
            ok(ex,"{\"totalBooks\":"+books.size()+",\"checkedOut\":"+co+",\"available\":"+(books.size()-co)+
                  ",\"totalUsers\":"+users.size()+",\"totalTransactions\":"+txs.size()+"}");
        });

        server.setExecutor(null);
        server.start();
        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║   📚 LibraryOS Server is RUNNING!    ║");
        System.out.println("║                                      ║");
        System.out.println("║   Open your browser and go to:       ║");
        System.out.println("║   http://localhost:8080              ║");
        System.out.println("║                                      ║");
        System.out.println("║   Press Ctrl+C to stop               ║");
        System.out.println("╚══════════════════════════════════════╝");
    }
}
