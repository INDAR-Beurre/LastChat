package smoke;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Minimal stand-in for the relay gateway.
 *
 * <p>Serves a fixed catalog and replays a scripted SSE stream so the backend's streaming path
 * can be exercised without network access or a credential.
 */
final class FakeRelay implements AutoCloseable {

    private final ServerSocket serverSocket;
    private final Thread acceptor;
    private volatile String catalogJson;
    private volatile String[] chunks = new String[0];
    private volatile String authorizationSeen;
    private volatile boolean running = true;

    FakeRelay() throws IOException {
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 16);
        acceptor = new Thread(this::acceptLoop, "fake-relay");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    int port() {
        return serverSocket.getLocalPort();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + port();
    }

    String authorizationSeen() {
        return authorizationSeen;
    }

    /** Sets the exact catalog JSON the relay returns. */
    void catalog(String json) {
        this.catalogJson = json;
    }

    /** Sets the SSE `data:` payloads to replay, in order, before `[DONE]`. */
    void stream(String... dataChunks) {
        this.chunks = dataChunks;
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket s = serverSocket.accept();
                Thread t = new Thread(() -> handle(s), "fake-relay-conn");
                t.setDaemon(true);
                t.start();
            } catch (IOException e) {
                return;
            }
        }
    }

    private void handle(Socket socket) {
        try {
            socket.setSoTimeout(30000);
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            OutputStream out = socket.getOutputStream();

            String requestLine = in.readLine();
            if (requestLine == null) {
                return;
            }
            String line;
            int contentLength = 0;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                if (line.toLowerCase().startsWith("content-length:")) {
                    contentLength = Integer.parseInt(line.substring(15).trim());
                } else if (line.toLowerCase().startsWith("authorization:")) {
                    authorizationSeen = line.substring("authorization:".length()).trim();
                }
            }
            for (int i = 0; i < contentLength; i++) {
                in.read();
            }

            String[] parts = requestLine.split(" ");
            String path = parts.length > 1 ? parts[1] : "/";

            if (path.startsWith("/v1/models")) {
                String body = catalogJson == null ? "{}" : catalogJson;
                writeAll(out, "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: "
                        + body.getBytes(StandardCharsets.UTF_8).length + "\r\nConnection: close\r\n\r\n"
                        + body);
            } else if (path.startsWith("/v1/chat/completions")) {
                writeAll(out, "HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\n"
                        + "Cache-Control: no-cache\r\nConnection: close\r\n\r\n");
                out.flush();
                for (String chunk : chunks) {
                    writeAll(out, "data: " + chunk + "\n\n");
                    out.flush();
                    Thread.sleep(5);
                }
                writeAll(out, "data: [DONE]\n\n");
            } else {
                writeAll(out, "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n");
            }
            out.flush();
        } catch (Exception ignored) {
            // A dropped connection is not a test failure; assertions run against recorded state.
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
                // best effort
            }
        }
    }

    private static void writeAll(OutputStream out, String s) throws IOException {
        out.write(s.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    @Override
    public void close() {
        running = false;
        try {
            serverSocket.close();
        } catch (IOException ignored) {
            // best effort
        }
    }
}
