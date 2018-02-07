package edu.illinois.cs425.team4.service;

import edu.illinois.cs425.team4.utils.AvroUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.MessageToMessageDecoder;

import java.util.List;

/**
 * Handler for decode bytes into an avro object
 */
public class AvroMessageDecoder<T> extends MessageToMessageDecoder<ByteBuf> {

  private final Class<T> targetClass;

  AvroMessageDecoder(final Class<T> targetClass) {
    this.targetClass = targetClass;
  }

  @Override
  protected void decode(final ChannelHandlerContext ctx, final ByteBuf in, final List<Object> out) throws Exception {
    if (in.readableBytes() < 4) {
      return;
    }

    in.markReaderIndex();

    final int length = in.readInt();
    if (in.readableBytes() < length) {
      in.resetReaderIndex();
      return;
    }

    final byte[] decoded = new byte[length];
    in.readBytes(decoded);

    out.add(AvroUtils.deserialize(decoded, targetClass));
  }
}
