package edu.upenn.ds.team.mp2;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handler for outbound channels.
 */
public final class OutboundChannelHandler extends ChannelOutboundHandlerAdapter {

  private final Logger LOG = Logger.getLogger(OutboundChannelHandler.class.getName());

  @Override
  public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
    LOG.log(Level.FINER, "Exception is throw in outbound channel {0}", cause);
    ctx.close();
  }
}
