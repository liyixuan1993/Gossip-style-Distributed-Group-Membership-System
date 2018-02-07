package edu.upenn.ds.team.mp2;

import edu.upenn.ds.team.service.NettyNetworkService;
import org.apache.commons.cli.*;

import java.net.InetSocketAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Launcher
 */
/*
Laucher is the beginning of the whole program.
It parsing the cmd and initialze a service.
1. set the service port.
2. set the introducer of that service so that it can join the group.
3. set packet_drop rate to simulate that sometimes a server cannot receive msg from other server

For the failure detection or leaves, you cannot use a master or leader, since its
failure must be detected as well. 
However,to enable machines to join the group, you can have a fixed contact machine that all potential 
members know about (the “introducer”). When the contact is down, no new members can join the group until the
contact has rejoined – but failures should still be detected, and leaves allowed.
*/
public final class Launcher {

  private static final Logger LOG = Logger.getLogger(Launcher.class.getName());

  // Not be instantiated
  private Launcher() {}

/*
The Apache Commons CLI library provides an API for parsing command line options passed to programs. 
There are three stages to command line processing. They are the definition, parsing and interrogation 
stages. The following sections will discuss each of these stages in turn, and discuss how to implement 
them with CLI.

  
Format is as follows:
--conn [IP:PORT] --port [PORT_LOCAL] --packet_drop [DROP_RATE]
--conn is used to set the introducer's address [IP:PORT]. If it is not set, it is considered as the 
first node in the group. Thus, this option is always required except for the first server in the group.
--port is used to set the port of the server. The value is by default 7000.
--packet_drop is used to set Message Loss Rate between 0 and 1. the default value is 0.
*/
  private static CommandLine parseCommandLine(final String[] args) {
    //https://commons.apache.org/proper/commons-cli/usage.html
    //command line processing Stage 1 : definition
    final Options options = new Options();
    options.addOption(Option.builder()
        .longOpt("conn")//long operation name double slash --conn
        .argName("[IP]:[PORT]") //the name of the argument value for the usage statement.like description
        .desc("Connection information to an introducer")//a description of the function of the option
        .hasArg()//The hasArg indicate if the parameter accepts an argument.
        .build());
    options.addOption(Option.builder()
        .longOpt("port")
        .argName("PORT") 
        .desc(String.format("Port number (default=%d)", Server.DEFAULT_PORT))
        .hasArg()
        .build());
    options.addOption(Option.builder()
        .longOpt("packet_drop")
        .argName("[RATE]")
        .desc("Packet drop rate (default=0)")
        .hasArg()
        .build());

    try {
      //command line processing Stage 2 : parsing
      final CommandLineParser parser = new DefaultParser();
      return parser.parse(options, args);
    } catch (final ParseException e) {
      System.out.println(e.getMessage());
      return null;
    }
  }

  public static InetSocketAddress parseAddress(final String str) {
    //InetAddress是Java对IP地址的封装，代表互联网协议（IP）地址；
    /*
该类实现了可序列化接口，直接继承自Java.NET.SocketAddress类，类声明如下：
public class InetSocketAddress extends SocketAddress
此类实现 IP 套接字地址（IP 地址 + 端口号）。它还可以是一个对（主机名 + 端口号），在此情况下，将尝试解析主机名。
①public static InetSocketAddress createUnresolved(String host, int port)  根据主机名和端口号创建未解析的套接字地址。
    */
    final String[] split = str.split(":");
    return new InetSocketAddress(split[0], Integer.parseInt(split[1]));
  }
/*
user can create a server, it must have port, it may have introducer address
*/
  public static void main(final String[] args) throws InterruptedException {
    //command line processing Stage 3 : interrogation 
    final CommandLine cmd = parseCommandLine(args);
    if (cmd == null) {
      return;
    }
    //boolean hasOption : Indicate if the option has been specified or not
    if (cmd.hasOption("packet_drop")) {
      //String getOptionValue : Return the value of the option
      final double packetDropRate = Double.parseDouble(cmd.getOptionValue("packet_drop"));
      if (packetDropRate < 0.0D || packetDropRate > 1.0D) {
        System.out.println("Invalid packet drop rate:" + packetDropRate);
        return;
      }
      LOG.log(Level.INFO, "Packet drop rate = {0}", packetDropRate);
      NettyNetworkService.dropRate = packetDropRate;
    }
    //initialize a new server
    final Server.Builder serverBuilder = Server.newBuilder();
    //this port is the server itself.
    if (cmd.hasOption("port")) {
      serverBuilder.setPort(Integer.valueOf(cmd.getOptionValue("port")));
    }
    //[IP]:[PORT] this is introducer address
    if (cmd.hasOption("conn")) {
      serverBuilder.setIntroduerAddress(parseAddress(cmd.getOptionValue("conn")));
    }
    serverBuilder.build().run();
  }
}
