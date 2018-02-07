package edu.upenn.ds.team.mp2;

import java.net.InetSocketAddress;

/**
 * Utility class
 */
public final class Utils {

  private Utils() {
  }

  public static InetSocketAddress getAddressFromId(final Id id) {
/*
A socket address has two parts, an IP address and a port number.InetSocketAddress represents a socket 
address.
We can use the following constructors to create an object of the InetSocketAddress class:
InetSocketAddress(InetAddress addr,  int port)
InetSocketAddress(String hostname, int port)

getAddress(),getHostName(),getPort()
*/
    return new InetSocketAddress(id.getHostname().toString(), id.getPort());
  }
}
