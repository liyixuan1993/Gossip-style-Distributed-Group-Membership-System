package edu.upenn.ds.team.mp2.service;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;

import java.util.List;

/**
 * DummyEncoder
 */
public final class DummyEncoder extends MessageToMessageEncoder<ByteBuf> {
  @Override
  protected void encode(final ChannelHandlerContext ctx, final ByteBuf in, final List<Object> out) throws Exception {
    out.add(in);
  }
}