package cx.lehmann.vertx.mail.vertxmail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.mail.MessagingException;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailException;
import org.bouncycastle.crypto.Mac;
import org.bouncycastle.crypto.digests.MD5Digest;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.params.KeyParameter;
import org.vertx.java.core.Future;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.net.NetClient;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.platform.Container;

/*
  first implementation of a SMTP client
 */
// TODO: this is not really a verticle

public class MailVerticle {

  private Vertx vertx;
//  private Container container;
  private Handler<Void> finishedHandler;
  
  public MailVerticle(Vertx vertx, Container container, Handler<Void> finishedHandler) {
    this.vertx=vertx;
//    this.container=container;
    log=container.logger();
    this.finishedHandler=finishedHandler;
  }

  private void write(NetSocket netSocket, String str) {
    log.info("command: "+str);
    netSocket.write(str+"\r\n");
  }

  Logger log;
  NetSocket ns;

  Future<String> commandResult;
  private boolean capaStartTLS=false;
  private Set<String> capaAuth=Collections.emptySet();
  private boolean capa8BitMime=false;
  private int capaSize=0;

  Email email;
  String username;
  String pw;

//  public void start() {
//    log=container.logger();
//    log.info("starting");
//  }

  public void sendMail(Email email, String username, String password) {
    this.email=email;
    this.username=username;
    pw=password;
    NetClient client = vertx.createNetClient();

    client.connect(Integer.parseInt(email.getSmtpPort()), email.getHostName(), asyncResult -> {
      if (asyncResult.succeeded()) {
        ns=asyncResult.result();
        commandResult=new CommandResultFuture(event -> serverGreeting(event));
        final Handler<Buffer> mlp=new MultilineParser(buffer -> commandResult.setResult(buffer.toString()));
        ns.dataHandler(mlp);
      } else {
        log.error("exception", asyncResult.cause());
      }
    });
  }

  private void serverGreeting(String buffer) {
    log.info("server greeting: "+buffer);
    ehloCmd();
  }

  private void ehloCmd() {
    write(ns, "EHLO windows7");
    commandResult=new CommandResultFuture(buffer -> {
      log.info("EHLO result: "+buffer);
      List<String> capabilities=parseEhlo(buffer);
      for(String c:capabilities) {
        if(c.equals("STARTTLS")) {
          capaStartTLS=true;
        }
        if(c.startsWith("AUTH ")) {
          capaAuth=new HashSet<String>(Arrays.asList(c.substring(5).split(" ")));
        }
        if(c.equals("8BITMIME")) {
          capa8BitMime=true;
        }
        if(c.startsWith("SIZE ")) {
          capaSize=Integer.parseInt(c.substring(5));
        }
      }

      if(capaStartTLS && !ns.isSsl() && (email.isStartTLSRequired() || email.isStartTLSEnabled())) {
        // avoid starting TLS if we already have connected with SSL or are in TLS
        startTLSCmd();
      } else {
        if(!ns.isSsl() && email.isStartTLSRequired()) {
          log.warn("STARTTLS required but not supported by server");
        } else {
          // TODO: this assumes that auth is always required if present
          if(!capaAuth.isEmpty()) {
            authCmd();
          } else {
            mailFromCmd();
          }
        }
      }
    });
  }

  /**
   * 
   */
  private void startTLSCmd() {
    write(ns,"STARTTLS");
    commandResult=new CommandResultFuture(buffer -> {
      log.info("STARTTLS result: "+buffer);
      upgradeTLS();
    });
  }

  private void upgradeTLS() {
    ns.ssl(v -> {
      log.info("ssl started");
      ehloCmd();
    });
  }

  private List<String> parseEhlo(String string) {
    // parse ehlo and other multiline replies
    List<String> v=new ArrayList<String>();

    String resultCode=string.substring(0,3);

    for(String l:string.split("\n")) {
      if(!l.startsWith(resultCode) || l.charAt(3)!='-' && l.charAt(3)!=' ') {
        log.error("format error in ehlo response");
      } else {
        v.add(l.substring(4));
      }
    }

    return v;
  }

  private void authCmd() {
    if(capaAuth.contains("CRAM-MD5")) {
      write(ns, "AUTH CRAM-MD5");
      commandResult=new CommandResultFuture(buffer -> {
        log.info("AUTH result: "+buffer);
        cramMD5Step1(buffer.substring(4));
      });
    }
    else if(capaAuth.contains("PLAIN")) {
      String authdata=base64("\0"+username+"\0"+pw);
      write(ns, "AUTH PLAIN "+authdata);
      commandResult=new CommandResultFuture(buffer -> {
        log.info("AUTH result: "+buffer);
        if(!buffer.toString().startsWith("2")) {
          log.warn("authentication failed");
        } else {
          mailFromCmd();
        }
      });
    }
    else if(capaAuth.contains("LOGIN")) {
      write(ns, "AUTH LOGIN");
      commandResult=new CommandResultFuture(buffer -> {
        log.info("AUTH result: "+buffer);
        sendUsername();
      });
    } else {
      log.warn("cannot find supported auth method");
    }
  }

  private void cramMD5Step1(String string) {
    String message=decodeb64(string);
    log.info("message "+message);
    String reply=hmacMD5hex(message,pw);
    write(ns, base64(username+" "+reply));
    commandResult=new CommandResultFuture(buffer -> {
      log.info("AUTH step 2 result: "+buffer);
      cramMD5Step2(buffer);
    });
  }

  private String hmacMD5hex(String message, String pw) {
    KeyParameter keyparameter;
    try {
      keyparameter = new KeyParameter(pw.getBytes("utf-8"));
      Mac mac=new HMac(new MD5Digest());
      mac.init(keyparameter);
      byte messageBytes[]=message.getBytes("utf-8");
      mac.update(messageBytes,0,messageBytes.length);
      byte outBytes[]=new byte[mac.getMacSize()];
      mac.doFinal(outBytes, 0);
      return Hex.encodeHexString(outBytes);
    } catch (UnsupportedEncodingException e) {
      // doesn't happen, auth will fail in that case
      return "";
    }
  }

  private void cramMD5Step2(String string) {
    log.info(string);
    if(string.startsWith("2")) {
      mailFromCmd();
    } else {
      log.warn("authentication failed");
    }
  }

  private void sendUsername() {
    write(ns, base64(username));
    commandResult=new CommandResultFuture(buffer -> {
      log.info("username result: "+buffer);
      sendPw();
    });
  }

  private void sendPw() {
    write(ns, base64(pw));
    commandResult=new CommandResultFuture(buffer -> {
      log.info("username result: "+buffer);
      if(!buffer.toString().startsWith("2")) {
        log.warn("authentication failed");
      } else {
        mailFromCmd();
      }
    });
  }

  private void mailFromCmd() {
    try {
      // prefer bounce address over from address
      // currently (1.3.3) commons mail is missing the getter for bounceAddress
      // I have requested that https://issues.apache.org/jira/browse/EMAIL-146
      String fromAddr=email.getFromAddress().getAddress();
      if(email instanceof BounceGetter) {
        String bounceAddr=((BounceGetter)email).getBounceAddress();
        if(bounceAddr!=null && !bounceAddr.isEmpty()) {
          fromAddr=bounceAddr;
        }
      }
      InternetAddress.parse(fromAddr,true);
      write(ns, "MAIL FROM:<"+fromAddr+">");
      commandResult=new CommandResultFuture(buffer -> {
        log.info("MAIL FROM result: "+buffer);
        rcptToCmd();
      });
    } catch (AddressException e) {
      log.error("address exception",e);
    }
  }

  private void rcptToCmd() {
    try {
      // FIXME: have to handle all addresses
      String toAddr=email.getToAddresses().get(0).getAddress();
      InternetAddress.parse(toAddr, true);
      write(ns, "RCPT TO:<"+toAddr+">");
      commandResult=new CommandResultFuture(buffer -> {
        log.info("RCPT TO result: "+buffer);
        dataCmd();
      });
    }
    catch(AddressException e) {
      log.error("address exception",e);
    }
  }

  private void dataCmd() {
    write(ns, "DATA");
    commandResult=new CommandResultFuture(buffer -> {
      log.info("DATA result: "+buffer);
      sendMaildata();
    });
  }

  private void sendMaildata() {
    ByteArrayOutputStream bos=new ByteArrayOutputStream();
    try {
      email.buildMimeMessage();
      email.getMimeMessage().writeTo(bos);
    } catch (IOException | MessagingException | EmailException e) {
      log.error("cannot create mime message",e);
    }
    // convert message to escape . at the start of line
    // TODO: this is probably bad for large messages
    String mailmessage=bos.toString()
        .replaceAll("\n\\.", "\n..")
        +"\r\n.";
    write(ns, mailmessage);
    commandResult=new CommandResultFuture(buffer -> {
      log.info("maildata result: "+buffer);
      quitCmd();
    });
  }

  private void quitCmd() {
    write(ns, "QUIT");
    commandResult=new CommandResultFuture(buffer -> {
      log.info("QUIT result: "+buffer);
      shutdownConnection();
    });
  }

  private void shutdownConnection() {
    ns.close();
    finishedHandler.handle(null);
  }

  private String base64(String string) {
    try {
      // this call does not create multi-line base64 data
      // (if someone uses a password longer than 57 chars or
      // one of the other SASL replies is longer than 76 chars)
      return Base64.encodeBase64String(string.getBytes("UTF-8"));
    } catch (UnsupportedEncodingException e) {
      // doesn't happen
      return "";
    }
  }

  private String decodeb64(String string) {
    try {
      return new String(Base64.decodeBase64(string), "UTF-8");
    } catch (UnsupportedEncodingException e) {
      // doesn't happen
      return "";
    }
  }

}
