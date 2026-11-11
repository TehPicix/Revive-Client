package revivedevelopment.reviveclient.utils.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Date;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import revivedevelopment.reviveclient.utils.other.JsonDateDeserializer;

public class Http {
   public static final int SUCCESS = 200;
   public static final int BAD_REQUEST = 400;
   public static final int UNAUTHORIZED = 401;
   public static final int FORBIDDEN = 403;
   public static final int NOT_FOUND = 404;
   private static final HttpClient CLIENT = HttpClient.newHttpClient();
   private static final Gson GSON = new GsonBuilder().registerTypeAdapter(Date.class, new JsonDateDeserializer()).create();

   public static Http.Request get(String url) {
      return new Http.Request(Http.Method.GET, url);
   }

   public static Http.Request post(String url) {
      return new Http.Request(Http.Method.POST, url);
   }

   private static enum Method {
      GET,
      POST;
   }

   public static class Request {
      private final Builder builder;
      private Http.Method method;
      private Consumer<Exception> exceptionHandler = Throwable::printStackTrace;

      private Request(Http.Method method, String url) {
         try {
            this.builder = HttpRequest.newBuilder()
               .uri(new URI(url))
               .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Safari/537.36");
            this.method = method;
         } catch (URISyntaxException var4) {
            throw new IllegalArgumentException(var4);
         }
      }

      public Http.Request header(String name, String value) {
         this.builder.header(name, value);
         return this;
      }

      public Http.Request bearer(String token) {
         this.builder.header("Authorization", "Bearer " + token);
         return this;
      }

      public Http.Request bodyString(String string) {
         this.builder.header("Content-Type", "text/plain");
         this.builder.method(this.method.name(), BodyPublishers.ofString(string));
         this.method = null;
         return this;
      }

      public Http.Request bodyForm(String string) {
         this.builder.header("Content-Type", "application/x-www-form-urlencoded");
         this.builder.method(this.method.name(), BodyPublishers.ofString(string));
         this.method = null;
         return this;
      }

      public Http.Request bodyJson(String string) {
         this.builder.header("Content-Type", "application/json");
         this.builder.method(this.method.name(), BodyPublishers.ofString(string));
         this.method = null;
         return this;
      }

      public Http.Request bodyJson(Object object) {
         this.builder.header("Content-Type", "application/json");
         this.builder.method(this.method.name(), BodyPublishers.ofString(Http.GSON.toJson(object)));
         this.method = null;
         return this;
      }

      public Http.Request ignoreExceptions() {
         this.exceptionHandler = e -> {};
         return this;
      }

      public Http.Request exceptionHandler(Consumer<Exception> exceptionHandler) {
         this.exceptionHandler = exceptionHandler;
         return this;
      }

      private <T> HttpResponse<T> _sendResponse(String accept, BodyHandler<T> responseBodyHandler) {
         this.builder.header("Accept", accept);
         if (this.method != null) {
            this.builder.method(this.method.name(), BodyPublishers.noBody());
         }

         HttpRequest request = this.builder.build();

         try {
            return Http.CLIENT.send(request, responseBodyHandler);
         } catch (InterruptedException | IOException var5) {
            this.exceptionHandler.accept(var5);
            return new FailedHttpResponse<>(request, var5);
         }
      }

      @Nullable
      private <T> T _send(String accept, BodyHandler<T> responseBodyHandler) {
         HttpResponse<T> res = this._sendResponse(accept, responseBodyHandler);
         return res.statusCode() == 200 ? res.body() : null;
      }

      public void send() {
         this._send("*/*", BodyHandlers.discarding());
      }

      public HttpResponse<Void> sendResponse() {
         return this._sendResponse("*/*", BodyHandlers.discarding());
      }

      @Nullable
      public InputStream sendInputStream() {
         return this._send("*/*", BodyHandlers.ofInputStream());
      }

      public HttpResponse<InputStream> sendInputStreamResponse() {
         return this._sendResponse("*/*", BodyHandlers.ofInputStream());
      }

      @Nullable
      public String sendString() {
         return this._send("*/*", BodyHandlers.ofString());
      }

      public HttpResponse<String> sendStringResponse() {
         return this._sendResponse("*/*", BodyHandlers.ofString());
      }

      @Nullable
      public Stream<String> sendLines() {
         return this._send("*/*", BodyHandlers.ofLines());
      }

      public HttpResponse<Stream<String>> sendLinesResponse() {
         return this._sendResponse("*/*", BodyHandlers.ofLines());
      }

      @Nullable
      public <T> T sendJson(Type type) {
         return this._send("application/json", JsonBodyHandler.ofJson(Http.GSON, type));
      }

      public <T> HttpResponse<T> sendJsonResponse(Type type) {
         return this._sendResponse("*/*", JsonBodyHandler.ofJson(Http.GSON, type));
      }
   }
}
