package webserver;

import db.DataBase;
import model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.HttpRequestUtils;
import util.IOUtils;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public class RequestHandler extends Thread {
    private static final Logger log = LoggerFactory.getLogger(RequestHandler.class);

    private Socket connection;
    private String url;

    public RequestHandler(Socket connectionSocket) {
        this.connection = connectionSocket;
    }

    public void run() {
        log.debug("New Client Connect! Connected IP : {}, Port : {}", connection.getInetAddress(),
                connection.getPort());

        try (InputStream in = connection.getInputStream(); OutputStream out = connection.getOutputStream()) {
            BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String line = br.readLine();
            if (line == null) {
                return;
            }
            String url =  HttpRequestUtils.getUrl(line);
            Map<String,String> headers = new HashMap<>();
            log.debug("headers: {}",line);
            while(!"".equals(line)) {
                line = br.readLine();
                log.debug("headers: {}",line);
                String[] headerTokens = line.split(": ");
                if (headerTokens.length == 2) {
                    headers.put(headerTokens[0], headerTokens[1]);
                }
            }

            log.debug("Content-length: {}",headers.get("Content-Length"));

            if (url.startsWith("/user/create")) {
                String requestBody = IOUtils.readData(br,Integer.parseInt(headers.get("Content-Length")));
                log.debug("Body: " ,requestBody);
                Map<String, String> params = HttpRequestUtils.parseQueryString(requestBody);

                User user = new User(params.get("userId"),params.get("password"),params.get("name"),params.get("email"));
                log.debug("User: {}",user);
                DataBase.addUser(user);
                DataOutputStream dos = new DataOutputStream(out);
                response302Header(dos);
            }

            else if (url.equals("/user/login")) {
                String requestBody = IOUtils.readData(br,Integer.parseInt(headers.get("Content-Length")));
                log.debug("Body: " ,requestBody);
                Map<String, String> params = HttpRequestUtils.parseQueryString(requestBody);
                log.debug("userId: {} password : {} name : {} email {}",params.get("userId"),params.get("password"),params.get("name"),params.get("email"));
                User user = DataBase.getUser(params.get("userId"));
                if (user == null) {
                    log.debug("User Not Found!");
                    DataOutputStream dos = new DataOutputStream(out);
                    response302Header(dos);
                }

                if (params.get("password").equals(user.getPassword())) {
                    log.debug("login success!");
                    DataOutputStream dos = new DataOutputStream(out);
                    response302HeaderWithCookie(dos,"logined=true");
                } else {
                    log.debug("password mismatch");
                    DataOutputStream dos = new DataOutputStream(out);
                    response302Header(dos);
                }
                DataOutputStream dos = new DataOutputStream(out);
                response302HeaderWithCookie(dos,"logined=true");
            }

            else {
                DataOutputStream dos = new DataOutputStream(out);
                byte[] body = Files.readAllBytes(new File("./webapp" + url).toPath());
                response200Header(dos, body.length);
                responseBody(dos, body);
            }



        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void response302Header(DataOutputStream dos) {
        try {
            dos.writeBytes("HTTP/1.1 302 Found \r\n");
            dos.writeBytes("Location: /index.html\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void response302HeaderWithCookie(DataOutputStream dos, String cookie) {
        try {
            dos.writeBytes("HTTP/1.1 302 Found \r\n");
            dos.writeBytes("Location: /index.html\r\n");
            dos.writeBytes("Set-Cookie: "+cookie+"\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void response200Header(DataOutputStream dos, int lengthOfBodyContent) {
        try {
            dos.writeBytes("HTTP/1.1 200 OK \r\n");
            dos.writeBytes("Content-Type: text/html;charset=utf-8\r\n");
            dos.writeBytes("Content-Length: " + lengthOfBodyContent + "\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void responseBody(DataOutputStream dos, byte[] body) {
        try {
            dos.write(body, 0, body.length);
            dos.flush();
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }
}
