package edu.upenn.ds.team.mp2;

import com.google.common.cache.Cache;
import edu.upenn.ds.team..service.ChannelHandlerFactory;
import edu.upenn.ds.team..service.NettyNetworkService;
import io.netty.channel.ChannelHandler;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for creating new {@link ServerInboundChannelHandler} instances
 */
public final class ServerInboundChannelHandlerFactory implements ChannelHandlerFactory {

  private final Id id;
  private final ConcurrentHashMap<Id, Status> membershipTable;
  private final Cache<Id, Status> changeTable;
  private final ConcurrentHashMap<Id, Long> suspectTable;
  private final BlockingQueue<Id> ackReceivedIds;
  private NettyNetworkService networkService;

  ServerInboundChannelHandlerFactory(final Id id,
                                     final ConcurrentHashMap<Id, Status> membershipTable,
                                     final Cache<Id, Status> changeTable,
                                     final ConcurrentHashMap<Id, Long> suspectTable,
                                     final BlockingQueue<Id> ackReceivedIds) {
    this.id = id;
    this.membershipTable = membershipTable;
    this.changeTable = changeTable;
    this.suspectTable = suspectTable;
    this.ackReceivedIds = ackReceivedIds;
  }

  public void setNetworkService(final NettyNetworkService networkService) {
    this.networkService = networkService;
  }

  @Override
  public ChannelHandler newInstance() {
    return new ServerInboundChannelHandler(id, membershipTable, changeTable, suspectTable, ackReceivedIds, networkService);
  }
}
