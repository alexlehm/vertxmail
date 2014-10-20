package cx.lehmann.vertx.mail.vertxmail;

import org.apache.commons.mail.SimpleEmail;

public class MySimpleEmail extends SimpleEmail implements BounceGetter {

  public String getBounceAddress() {
    return bounceAddress;
  }
  
}
