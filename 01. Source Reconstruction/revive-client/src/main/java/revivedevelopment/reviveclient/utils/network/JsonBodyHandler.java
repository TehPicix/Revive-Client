package revivedevelopment.reviveclient.utils.network;

import com.google.gson.Gson;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodySubscriber;
import java.net.http.HttpResponse.BodySubscribers;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow.Subscription;

public record JsonBodyHandler<T>(BodySubscriber<InputStream> delegate, Gson gson, Type type) implements BodySubscriber<T> {
   public static <T> BodyHandler<T> ofJson(Gson gson, Type type) {
      return responseInfo -> new JsonBodyHandler<>(BodySubscribers.ofInputStream(), gson, type);
   }

   @Override
   public CompletionStage<T> getBody() {
      return this.delegate.getBody().thenApply(in -> (T)(in == null ? null : this.gson.fromJson(new InputStreamReader(in), this.type)));
   }

   @Override
   public void onSubscribe(Subscription subscription) {
      this.delegate.onSubscribe(subscription);
   }

   public void onNext(List<ByteBuffer> item) {
      this.delegate.onNext(item);
   }

   @Override
   public void onError(Throwable throwable) {
      this.delegate.onError(throwable);
   }

   @Override
   public void onComplete() {
      this.delegate.onComplete();
   }
}
