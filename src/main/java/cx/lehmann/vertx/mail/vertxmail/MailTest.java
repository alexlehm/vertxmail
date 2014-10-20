package cx.lehmann.vertx.mail.vertxmail;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.HtmlEmail;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.platform.Verticle;

/*
  first implementation of a SMTP client
 */
public class MailTest extends Verticle {

  Logger log;

  public void start() {
    log=container.logger();
    log.info("starting");

    try {
      // TODO: this is not asynchronous
      // this is a hack to avoid putting an actual account into the test
      // script, you will have to put your own account into the file
      // or write the account data directly into the java code
      // or a vertx conf file
      Properties account=new Properties();
      InputStream inputstream=new FileInputStream("account.properties");
      account.load(inputstream);
      sendMail(account.getProperty("username"), account.getProperty("pw"));
    }
    catch(IOException ioe) {
      log.error("IOException", ioe);
    }
  }

  /**
   * 
   */
  private void sendMail(String username, String pw) {
    try {
      HtmlEmail email=new MyHtmlEmail(); // this provides the missing getter for bounceAddress

      email.setCharset("UTF-8");

      email.setHostName("mail.arcor.de");
      email.setSmtpPort(587);
      email.setStartTLSRequired(true);

      // Create the email message
      email.addTo("lehmann333@arcor.de", "User");
      email.setFrom("lehmann333@arcor.de", "Sender");
      email.setBounceAddress("user@example.com");
      email.setSubject("Test email with HTML");

      // set the html message
      email.setHtmlMsg("<html>This is a test message from <a href=\"http://vertx.io\">Vert.x</a></html>");

      // set the alternative message
      email.setTextMsg("Your email client does not support HTML messages");

      // OK, its not really a verticle, we will get that right later ...
      MailVerticle mailVerticle=new MailVerticle(vertx,container,v -> log.info("mail finished"));
      mailVerticle.sendMail(email,username,pw);

    } catch (EmailException e) {
      log.error("Exception",e);
    }
  }

}
