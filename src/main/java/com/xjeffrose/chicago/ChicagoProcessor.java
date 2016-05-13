package com.xjeffrose.chicago;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.xjeffrose.chicago.DBManager;
import com.xjeffrose.chicago.Op;
import com.xjeffrose.xio.processor.XioProcessor;
import com.xjeffrose.xio.server.RequestContext;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import java.util.List;

public class ChicagoProcessor implements XioProcessor {
  private DBManager dbManager;

  public ChicagoProcessor(DBManager dbManager) {
    this.dbManager = dbManager;
  }

  @Override
  public void disconnect(ChannelHandlerContext ctx) {

  }

  private byte[] createResponse(boolean status, byte[] response) {
    ChicagoObjectEncoder encoder = new ChicagoObjectEncoder();

    if (response == null) {
      response = "x".getBytes();
    }

    return encoder.encode(Op.fromInt(3), Boolean.toString(status).getBytes(), response);
  }

  @Override
  public ListenableFuture<Boolean> process(ChannelHandlerContext ctx, Object req, RequestContext reqCtx) {
    ListeningExecutorService service = MoreExecutors.listeningDecorator(ctx.executor());
    ChicagoMessage msg = null;

    if (req instanceof ChicagoMessage) {
      msg = (ChicagoMessage) req;
    }

    ChicagoMessage finalMsg = msg;
    return service.submit(() -> {

      if (finalMsg == null) {
        ctx.writeAndFlush(new DefaultChicagoMessage(Op.fromInt(3), Boolean.toString(false).getBytes(), "x".getBytes()));

        return false;
      }

      byte[] readResponse = null;
      boolean status = false;

      switch (finalMsg.getOp()) {
        case READ:
          readResponse = dbManager.read(finalMsg.getKey());
          if (readResponse != null) {
            status = true;
          }
          break;
        case WRITE:
          status = dbManager.write(finalMsg.getKey(), finalMsg.getVal());
          break;
        case DELETE:
          status = dbManager.delete(finalMsg.getKey());
        default:
          break;
      }

      ctx.writeAndFlush(new DefaultChicagoMessage(Op.fromInt(3), Boolean.toString(status).getBytes(), readResponse));
      return true;
    });
  }
}