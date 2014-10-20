package cx.lehmann.vertx.mail.vertxmail;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Future;
import org.vertx.java.core.Handler;

public class CommandResultFuture implements Future<String> {

  private Handler<AsyncResult<String>> handler;
  private String result;
  private Throwable cause;

  // for some reason eclipse 4.4 thinks this is unused
  // probably a bug with the lambda function
  @SuppressWarnings("unused")
  private Handler<String> stringHandler;

  public CommandResultFuture(Handler<String> handler) {
    super();
    stringHandler=handler;
    this.setHandler(event ->  {
      if(event.succeeded()) {
        String string=event.result();
        handler.handle(string);
      } else {
        // FIXME: logging
        event.cause().printStackTrace();
      }
    });
  }

  @Override
  public String result() {
    return result;
  }

  @Override
  public Throwable cause() {
    return cause;
  }

  @Override
  public boolean succeeded() {
    return result!=null;
  }

  @Override
  public boolean failed() {
    return cause!=null;
  }

  @Override
  public boolean complete() {
    return result!=null || cause!=null;
  }

  @Override
  public Future<String> setHandler(Handler<AsyncResult<String>> handler) {
    this.handler=handler;
    return this;
  }

  @Override
  public Future<String> setResult(String string) {
    result=string;
    handler.handle(this);
    return this;
  }

  @Override
  public Future<String> setFailure(Throwable throwable) {
    cause=throwable;
    handler.handle(this);
    return this;
  }

}
