package edu.upenn.ds.team.mp2.service;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.DatagramPacketDecoder;
import io.netty.handler.codec.DatagramPacketEncoder;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * NetworkService
 * Use UDP/IP
 * Caution: thread unsafe.
 */
public final class NettyNetworkService {

  private static final Logger LOG = Logger.getLogger(NettyNetworkService.class.getName());

  private final EventLoopGroup clientWorkerGroup;
  private final Bootstrap clientBootstrap;
  private final EventLoopGroup serverGroup;
  private final Bootstrap serverBootstrap;
  private Channel channel;
  private final ConcurrentHashMap<InetSocketAddress, Channel> outChannels = new ConcurrentHashMap<>();
  public static volatile double dropRate = 0.0D;
  private final Random random = new Random();

  private NettyNetworkService(final List<ChannelHandlerFactory> serverHandlerFactories,
                              final List<ChannelHandlerFactory> clientHandlerFactories,
                              final int serverGroupNum,
                              final Class<? extends Object> targetClass) {
    this.clientWorkerGroup = new NioEventLoopGroup();
    this.clientBootstrap = new Bootstrap()
        .group(clientWorkerGroup)
        .channel(NioDatagramChannel.class)
        .handler(new ChannelInitializer<DatagramChannel>() {
          @Override
          protected void initChannel(DatagramChannel datagramChannel) throws Exception {
            final ChannelPipeline pipeline = datagramChannel.pipeline();
            pipeline.addLast("udpEncoder", new DatagramPacketEncoder<>(new DummyEncoder()));
            pipeline.addLast("encoder", new AvroMessageEncoder<>(targetClass));
            for (int i = 0; i < clientHandlerFactories.size(); ++i) {
              pipeline.addLast("handler" + i, clientHandlerFactories.get(i).newInstance());
            }
          }
        });

    this.serverGroup = new NioEventLoopGroup(serverGroupNum);
    this.serverBootstrap = new Bootstrap()
        .group(serverGroup)
        .channel(NioDatagramChannel.class)
        .handler(new ChannelInitializer<DatagramChannel>() {
          @Override
          protected void initChannel(DatagramChannel datagramChannel) throws Exception {
            final ChannelPipeline pipeline = datagramChannel.pipeline();
            pipeline.addLast("decoder", new DatagramPacketDecoder(new AvroMessageDecoder<>(targetClass)));
            pipeline.addLast("udpEncoder", new DatagramPacketEncoder<>(new DummyEncoder()));
            pipeline.addLast("encoder", new AvroMessageEncoder<>(targetClass));
            for (int i = 0; i < serverHandlerFactories.size(); ++i) {
              pipeline.addLast("handler" + i, serverHandlerFactories.get(i).newInstance());
            }
          }
        });
  }

  public boolean send(final String host, final int port, final Object obj) {
    final Channel channel = open(host, port);
    if (channel != null) {
      return send(channel, obj);
    }
    return false;
  }

  public boolean send(final InetSocketAddress address, final Object obj) {

    final Channel channel = open(address);
    if (channel != null) {
      return send(channel, obj);
    }
    LOG.log(Level.WARNING, "Channel is null");
    return false;
  }

  public boolean send(final Channel channel, final Object obj) {
    if (random.nextDouble() < dropRate) {
      LOG.log(Level.FINER, "Dropping packet");
      return true;
    }

    if (channel != null && obj != null) {
      try {
        final ChannelFuture f = channel.writeAndFlush(obj).await();
        assert f.isDone();
        // assumes that this channel is going to close in client-side soon
        // leaves this channel without closing action.
        return f.isSuccess();
      } catch (final InterruptedException e) {
        LOG.log(Level.WARNING, "Failed to send a message", e);
      }
    }
    LOG.log(Level.WARNING, "Either channel or object is null");
    return false;
  }

  private Channel open(final InetSocketAddress address) {
    Channel c = outChannels.get(address);
    if (c != null) {
      if (c.isActive()) {
        LOG.log(Level.FINEST, "Re-use an existing channel to {0}", address);
        return c;
      } else {
        LOG.log(Level.FINEST, "Invalid connection to {0} is removed.", address);
        outChannels.remove(address, c);
      }
    }

    final ChannelFuture f = clientBootstrap.connect(address).awaitUninterruptibly();
    assert f.isDone();
    if (!f.isSuccess()) {
      LOG.log(Level.WARNING, "Connection failed.", f.cause());
      return null;
    } else {
      LOG.log(Level.FINEST, "A new channel to {0} is established", address);
      final Channel newChannel = f.channel();
      c = outChannels.putIfAbsent(address, newChannel);
      if (c != null) { // someone creates before
        return c;
      } else {
        return newChannel;
      }
    }
  }

  private Channel open(final String host, final int port) {
    return open(new InetSocketAddress(host, port));
  }

  public boolean start(final int port) {
    try {
      final ChannelFuture f = serverBootstrap.bind(port).await();
      assert f.isDone();
      if (!f.isSuccess()) {
        LOG.log(Level.WARNING, String.format("Binding failed for port=%d.", port), f.cause());
        return false;
      } else {
        channel = f.channel();
        return true;
      }
    } catch (final InterruptedException ie) {
      LOG.log(Level.SEVERE, "Exception occurred while binding", ie);
      serverGroup.shutdownGracefully();
      return false;
    }
  }

  public boolean waitForClose() {
    if (channel == null) {
      return false;
    }
    try {
      final ChannelFuture f = channel.closeFuture().await();
      if (!f.isSuccess()) {
        LOG.log(Level.WARNING, "Failed to wait for closing of binding channel.", f.cause());
        return false;
      }
      return true;
    } catch (final InterruptedException e) {
      LOG.log(Level.WARNING, "Failed to wait for closing of binding channel.", e);
      return false;
    }
  }

  public boolean stop() {
    if (channel == null) {
      return false;
    }
    try {
      final ChannelFuture f = channel.close().await();
      assert f.isDone();
      if (!f.isSuccess()) {
        LOG.log(Level.WARNING, "Failed to close binding channel.", f.cause());
        return false;
      }
      return true;
    } catch (final InterruptedException e) {
      LOG.log(Level.WARNING, "Failed to close a server", e);
      return false;
    } finally {
      clientShutdownGracefully();
      serverShutdownGracefully();
    }
  }

  public void clientShutdownGracefully() {
    clientWorkerGroup.shutdownGracefully();
  }

  public void serverShutdownGracefully() {
    serverGroup.shutdownGracefully();
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public final static class Builder {
    private List<ChannelHandlerFactory> serverChannelHandlerFactories = new ArrayList<>();
    private List<ChannelHandlerFactory> clientChannelHandlerFactories = new ArrayList<>();
    private int serverGroupNum = 4;
    private Class<? extends Object> targetClass;

    public Builder addServerChannelHandlerFactory(final ChannelHandlerFactory channelHandlerFactory) {
      serverChannelHandlerFactories.add(channelHandlerFactory);
      return this;
    }

    public Builder addClientChannelHandlerFactory(final ChannelHandlerFactory channelHandlerFactory) {
      clientChannelHandlerFactories.add(channelHandlerFactory);
      return this;
    }

    public Builder setServerGroupNum(final int serverGroupNum) {
      this.serverGroupNum = serverGroupNum;
      return this;
    }

    public Builder setMessageType(final Class<? extends Object> targetClass) {
      this.targetClass = targetClass;
      return this;
    }

    public NettyNetworkService build() {
      if (targetClass == null) {
        throw new IllegalArgumentException("Message type class should be given");
      }
      return new NettyNetworkService(serverChannelHandlerFactories, clientChannelHandlerFactories,
          serverGroupNum, targetClass);
    }
  }
}
