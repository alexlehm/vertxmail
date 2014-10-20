package cx.lehmann.vertx.mail.vertxmail;

import org.apache.commons.mail.HtmlEmail;

public class MyHtmlEmail extends HtmlEmail implements BounceGetter {
  public String getBounceAddress() {
    return bounceAddress;
  }
}
