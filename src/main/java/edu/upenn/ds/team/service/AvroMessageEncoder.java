package edu.upenn.ds.team.mp2.service;

import edu.upenn.ds.team.mp2.Message;
import edu.upenn.ds.team.utils.AvroUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import java.io.ObjectOutputStream;

/**
 * Handler for encoding bytes to an avro object
 */
public class AvroMessageEncoder<T> extends MessageToByteEncoder<Object> {

  private static final byte[] LENGTH_PLACEHOLDER = new byte[4];
  private final Class<T> targetClass;

  public AvroMessageEncoder(final Class<T> targetClass) {
    this.targetClass = targetClass;
  }

  @Override
  protected void encode(final ChannelHandlerContext channelHandlerContext, final Object msg, final ByteBuf out) throws Exception {
    int startIdx = out.writerIndex();

    ByteBufOutputStream bout = new ByteBufOutputStream(out);
    bout.write(LENGTH_PLACEHOLDER);
    bout.write(AvroUtils.serialize(targetClass.cast(msg), targetClass));

    int endIdx = out.writerIndex();

    out.setInt(startIdx, endIdx - startIdx - 4);
  }
}
