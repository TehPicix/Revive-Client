package revivedevelopment.reviveclient.utils.network;

import java.net.URI;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Version;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;

public record FailedHttpResponse<T>(HttpRequest request, Exception exception) implements HttpResponse<T> {
   @Override
   public int statusCode() {
      return 400;
   }

   @Override
   public Optional<HttpResponse<T>> previousResponse() {
      return Optional.empty();
   }

   @Override
   public HttpHeaders headers() {
      return HttpHeaders.of(Map.of(), (s1, s2) -> true);
   }

   @Override
   public T body() {
      return null;
   }

   @Override
   public Optional<SSLSession> sslSession() {
      return Optional.empty();
   }

   @Override
   public URI uri() {
      return this.request.uri();
   }

   @Nullable
   @Override
   public Version version() {
      return this.request.version().orElse(null);
   }
}
