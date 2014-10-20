package cx.lehmann.vertx.mail.vertxmail;

import java.util.logging.Logger;

import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.parsetools.RecordParser;

public class MultilineParser implements Handler<Buffer> {
  static Logger log=Logger.getLogger(MultilineParser.class.getName());
  boolean initialized=false;
  boolean crlfMode=false;
  Buffer result;
  Handler<Buffer> output;
  RecordParser rp;

  public MultilineParser(Handler<Buffer> output) {
    Handler<Buffer> mlp=new Handler<Buffer> () {

      @Override
      public void handle(Buffer buffer) {
        //        log.info("handle:\""+buffer+"\"");
        if(!initialized) {
          initialized=true;
          // process the first line to determine CRLF mode
          String line=buffer.toString();
          if(line.endsWith("\r")) {
            log.info("setting crlf line mode");
            crlfMode=true;
            rp.delimitedMode("\r\n");
            line=line.substring(0,line.length()-1);
            appendOrHandle(new Buffer(line));
          } else {
            appendOrHandle(buffer);
          }
        } else {
          appendOrHandle(buffer);
        }
      }

      private void appendOrHandle(Buffer buffer) {
        if(result==null) {
          result=buffer;
        } else {
          result.appendString("\n");
          result.appendBuffer(buffer);
        }
        if(isFinalLine(buffer)) {
          output.handle(result);
          result=null;
        }
      }

      private boolean isFinalLine(Buffer buffer) {
        String line=buffer.toString();
        return !line.matches("^\\d+-.*");
      }

    };

    RecordParser rp=RecordParser.newDelimited("\n", mlp);
    this.rp=rp;
    this.output=output;
  }

  @Override
  public void handle(Buffer event) {
    rp.handle(event);
  }

}
