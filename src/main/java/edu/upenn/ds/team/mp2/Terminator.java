package edu.upenn.ds.team.mp2;

import edu.upenn.ds.team.service.AvroMessageEncoder;
import edu.upenn.ds.team.service.DummyEncoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.DatagramPacketEncoder;
import org.apache.commons.cli.*;

import java.net.InetSocketAddress;
import java.util.List;

import static edu.upenn.ds.team.mp2.Launcher.parseAddress;

/**
 * Terminator that sends a STOP message to designated servers
 * it is a seperate program from launcher. we run this program to let some server terminate
 */
public final class Terminator {

  private final boolean crashFlag;
  private final EventLoopGroup workerGroup;
  private final Bootstrap bootstrap;

  private Terminator(final boolean crashFlag) {
    this.crashFlag = crashFlag;
    this.workerGroup = new NioEventLoopGroup();
    this.bootstrap = new Bootstrap()
        .group(workerGroup)
        .channel(NioDatagramChannel.class)
        .handler(new ChannelInitializer<DatagramChannel>() {
          @Override
          protected void initChannel(DatagramChannel datagramChannel) throws Exception {
            final ChannelPipeline pipeline = datagramChannel.pipeline();
            pipeline.addLast("udpEncoder", new DatagramPacketEncoder<>(new DummyEncoder()));
            pipeline.addLast("encoder", new AvroMessageEncoder<>(Message.class));
          }
        });
  }

  private Channel open(final InetSocketAddress address) {
    final ChannelFuture f = bootstrap.connect(address).awaitUninterruptibly();
    assert f.isDone();
    if (!f.isSuccess()) {
      System.out.println("Connection failed to " + address);
      f.cause().printStackTrace();
      return null;
    } else {
      return f.channel();
    }
  }

  private Type getMessageType() {
    return crashFlag? Type.CRASH : Type.TERMINATE;
  }

  private void sendTerminate(final InetSocketAddress address) {
    System.out.println("Send a " + getMessageType() + " message to " + address);
    final Channel c = open(address);
    if (c != null) {
      final Message msg = Message.newBuilder()
          .setType(getMessageType())
          .build();

      final ChannelFuture f = c.writeAndFlush(msg).awaitUninterruptibly();
      assert f.isDone();
      // assumes that this channel is going to close in client-side soon
      // leaves this channel without closing action.
      if (!f.isSuccess()) {
        System.out.println("Failed to send a " + getMessageType() + " message to " + address);
      }
    }
  }

  private void stop() {
    workerGroup.shutdownGracefully();
  }

  private static CommandLine parseCommandLine(final String[] args) {
    final Options options = new Options();
    options.addOption(Option.builder()
//The short opt is used to define an arg with a simple dash (-shortopt) and the long opt is defined with 
      //double dash (--longopt). so it should be --crash
        .longOpt("crash")
        .desc("Make given nodes crash")
        .build());
/*
Once you filled the Options instance with the arguments of the program, you can use it. You have to 
use a CommandLineParser to parse the Options.
CommandLineParser parser = new DefaultParser();
CommandLine cmd = parser.parse(options, args);

With that object(cmd), you can get the options that have been passed to the application. For that, you 
can use the given methods :

String getOptionValue : Return the value of the option
boolean hasOption : Indicate if the option has been specified or not
List getArgList() : Return all the args that are not specified args
String[] getArgs() : Return all the args that are not specified args
String getOptionValue : Return the value of the option

the cmd shuold like this : --crash [IP]:[PORT] [IP]:[PORT] [IP]:[PORT] or just [IP]:[PORT] [IP]:[PORT]
which means leave instead of crash
 */
  try {
      final CommandLineParser parser = new DefaultParser();
      return parser.parse(options, args);
    } catch (final ParseException e) {
      System.out.println(e.getMessage());
      return null;
    }
  }

  public static void main(final String[] args) {
    final CommandLine cmd = parseCommandLine(args);
    if (cmd == null) {
      return;
    }

    final List<String> argList = cmd.getArgList();
    if (argList.size() == 0) {
      System.out.println("Target server should be given");
      return;
    }
    //hasOption() : Query to see if an option has been set.
    final Terminator terminator = new Terminator(cmd.hasOption("crash"));

    try {
      for (final String str : argList) {
        terminator.sendTerminate(parseAddress(str));
      }
    } catch (final Exception e) {
      e.printStackTrace();
    } finally {
      terminator.stop();
    }
  }
}
