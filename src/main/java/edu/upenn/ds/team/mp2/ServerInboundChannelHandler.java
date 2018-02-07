package edu.upenn.ds.team.mp2;

import com.google.common.cache.Cache;
import edu.upenn.ds.team.service.NettyNetworkService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import static edu.upenn.ds.team.mp2.Utils.getAddressFromId;

/**
 * Handler for server-side in-bound channels.
 */
/*
Netty 是一个 Java NIO 客户端服务器框架，使用它可以快速简单地开发网络应用程序，比如服务器和客户端的协议。Netty 大大简化
了网络程序的开发过程比如 TCP 和 UDP 的 socket 服务的开发。
让我们从 handler （处理器）的实现开始，handler 是由 Netty 生成用来处理 I/O 事件的。

我们添加一个自己的Handler用于写自己的处理逻辑。
ServerInboundChannelHandler 继承自 SimpleChannelInboundHandler，这个类实现了ChannelInboundHandler接口，
ChannelInboundHandler 提供了许多事件处理的接口方法，然后你可以覆盖这些方法。现在仅仅只需要继承 
SimpleChannelInboundHandler 类而不是你自己去实现接口方法。

channelRead0 在这里的作用是类似于3.x版本的messageReceived()。可以当做是每一次收到消息是触发。

ServerInboundChannelHandler listen to other servers, it will receive six kinds of messages
1. ping: s sends back ACK on which Change Table is piggybacked to the PING sender
2. ACK: s updates its MT,CT and ST with the received table (another’s CT) in ACK
  1) mergeActive();if active originally,then do nothing,if is suspected before,modify membershipTable 
    and changeTable as Active. Remove it from suspectTable
  2) mergeSuspected() : modify it in membershipTable suspectTable,and changeTable
  3) mergeFailed() : remove from its membershipTable suspectTable,and set changeTable as failed(so it can
     response ack to other servers to tell them some serve is failed)
  Also, we should set the server who send me ACK as Active in MT and CT.
  And add its ID to receivedIds,so in server.java, I can check whether I communicate with that server 
  successfully,else set it suspected.
3. Join: If a server s receives JOIN, s takes a role of the introducer. s will update MT as active,
  Then it sends its Membership Table back in a JOIN_ACK and updates the new node’s status as JOIN in CT.   
4. JOIN_ACK: s initializes its MT in the message(CT from other sender) 
5. Terminate: remove itself from membershipTable,and put changetable as failed,note that after remove 
  itself from MT,even it receive other server that indicates it is active,it will not work anymore. which
  means mergeActive() and other methods will not work after Terminate
  Also, if terminate,which means leaves voluntarily. so it should tell others he leaves. so loop for another 3 times
6. Crash : exit(1) immediately.
*/
public class ServerInboundChannelHandler extends SimpleChannelInboundHandler<Message> {

  private static final Logger LOG = Logger.getLogger(ServerInboundChannelHandler.class.getName());

  private final Id id;
  private final ConcurrentHashMap<Id, Status> membershipTable;
  private final Cache<Id, Status> changeTable;
  private final ConcurrentHashMap<Id, Long> suspectTable;
  private final BlockingQueue<Id> receivedIds;
  private final NettyNetworkService networkService;
  private static volatile boolean stopFlag = false;
  /*
AtomicInteger，一个提供原子操作的Integer的类。在Java语言中，++i和i++操作并不是线程安全的，在使用的时候，不可避免的会用
到synchronized关键字。而AtomicInteger则通过一种线程安全的加减操作接口。
那么为什么不使用记数器自加呢，例如count++这样的，因为这种计数是线程不安全的，高并发访问时统计会有误
  */
  private static final AtomicInteger countForStop = new AtomicInteger(3);

  ServerInboundChannelHandler(final Id id,
                              final ConcurrentHashMap<Id, Status> membershipTable,
                              final Cache<Id, Status> changeTable,
                              final ConcurrentHashMap<Id, Long> suspectTable,
                              final BlockingQueue<Id> receivedIds,
                              final NettyNetworkService networkService) {
    this.id = id;
    this.membershipTable = membershipTable;
    this.changeTable = changeTable;
    this.suspectTable = suspectTable;
    this.receivedIds = receivedIds;
    this.networkService = networkService;
  }
  //Apache Avro for platform-independent message format, <Type><Sender_id><Table> . <Table> is an
  //array of <Entry>=<id, status>.
  // transform status table to list
  private static List<Entry> transformMapToList(final Map<Id, Status> table){
    final List<Entry> sentTable= new ArrayList<>();
    for (final Map.Entry<Id, Status> mapEntry: table.entrySet()){
      Entry messageEntry = new Entry(mapEntry.getKey(), mapEntry.getValue());
      sentTable.add(messageEntry);
    }
    return sentTable;
  }
  //if active originally,then do nothing,if is suspected before,modify membershipTable suspectTable,
  //and changeTable as Active
  private void mergeActive(final Id targetId) {
    final Status prevStatus = membershipTable.get(targetId);
    if (prevStatus == Status.SUSPECTED) {
      LOG.log(Level.INFO, "Update Server {0} Status from SUSPECTED to ACTIVE.", targetId);
      membershipTable.put(targetId, Status.ACTIVE);
      changeTable.put(targetId, Status.ACTIVE);
      suspectTable.remove(targetId);

    } else if (prevStatus != null && prevStatus != Status.ACTIVE) {
      LOG.log(Level.FINE, "Unexpected status transition for {0} from {1} to ACTIVE",
          new Object[]{targetId, prevStatus});
    }
  }
  //modify it in membershipTable suspectTable,and changeTable
  private void mergeSuspected(final Id targetId) {
    final Status prevStatus = membershipTable.get(targetId);
    if (prevStatus == Status.ACTIVE){
      //if targeId = id, then it is wrong because I know myself is active 
      if (targetId.equals(id)) {
        changeTable.put(targetId, Status.ACTIVE);
      } else {
        LOG.log(Level.INFO, "Update Server {0} Status from ACTIVE to SUSPECTED.", targetId);
        membershipTable.put(targetId, Status.SUSPECTED);
        changeTable.put(targetId, Status.SUSPECTED);
        suspectTable.putIfAbsent(targetId, System.currentTimeMillis());
      }

    } else if (prevStatus != null && prevStatus != Status.SUSPECTED) {
      LOG.log(Level.INFO, "Unexpected status transition for {0} from {1} to SUSPECTED",
          new Object[]{targetId, prevStatus});
    }
  }
  //remove from its membershipTable suspectTable,and set changeTable as failed
  private void mergeFailed(final Id targetId) {
    if (targetId.equals(id)) {
      // false positive
      LOG.log(Level.INFO, "False positive for {0}", targetId);
      return;
    }

    if (membershipTable.get(targetId) != null) {
      membershipTable.remove(targetId);
      suspectTable.remove(targetId);
      changeTable.put(targetId, Status.FAILED);
      LOG.log(Level.INFO, "Server {0} is considered FAILED.", targetId);
    }
  }
  //put the serve into membershipTable
  private void mergeJoin(final Id targetId) {
    if (membershipTable.putIfAbsent(targetId, Status.ACTIVE) == null) {
      LOG.log(Level.INFO, "Sever {0} joined. Set it ACTIVE", targetId);
    }
  }

  @Override
  protected void channelRead0(final ChannelHandlerContext ctx, final Message msg) throws Exception {
    //if terminate,then remove itself from membershipTable,and put changetable as failed
    if (msg.getType() == Type.TERMINATE) {
      LOG.log(Level.INFO, "Receives a TERMINATE message. Marks itself as failed and waits until it disseminates info");
      membershipTable.remove(id);
      changeTable.put(id, Status.FAILED);
      stopFlag = true;
      return;

    } else if (msg.getType() == Type.CRASH) {
      LOG.log(Level.INFO, "Receives a CRASH message");
      // Just crash
      System.exit(1);
    }

    LOG.log(Level.FINEST, "Receive a {0} message from {1}", new Object[]{msg.getType(), msg.getSenderId()});
   //When it receives PING , s sends back ACK on which Change Table is piggybacked to the PING sender.
    if (msg.getType() == Type.PING) {
      // send reply
      final Message response = Message.newBuilder()
          .setSenderId(id)
          .setType(Type.ACK)
          .setTable(transformMapToList(changeTable.asMap()))
          .build();
      networkService.send(getAddressFromId(msg.getSenderId()), response);
    } 
//If a server s receives JOIN, s takes a role of the introducer. It sends its Membership Table
    //back in a JOIN_ACK and updates the new node’s status as JOIN in CT.   
    else if (msg.getType() == Type.JOIN){
      LOG.log(Level.INFO, "Server {0} joins the network. Updates its status to ACTIVE", msg.getSenderId());
      //add to membershipTable
      membershipTable.put(msg.getSenderId(), Status.ACTIVE);
      LOG.log(Level.INFO, "Add JOIN into the change table for {0}", msg.getSenderId());
      changeTable.put(msg.getSenderId(), Status.JOIN);
      //Send back membershipTable
      final Message response = Message.newBuilder()
          .setSenderId(id)
          .setType(Type.JOIN_ACK)
          .setTable(transformMapToList(membershipTable))
          .build();
      networkService.send(getAddressFromId(msg.getSenderId()), response);

    } 
//On receiving JOIN_ACK, s initializes its MT in the message and sets its status in CT as JOIN .
    else if (msg.getType() == Type.JOIN_ACK) {
      // Put all the elements in the received membership table into me.
      for (final Entry entry : msg.getTable()) {
        if (membershipTable.put(entry.getId(), entry.getStatus()) != entry.getStatus()) {
          LOG.log(Level.INFO, "Update Server {0} status to {1}", new Object[]{entry.getId(), entry.getStatus()});
        }
      }
      receivedIds.offer(msg.getSenderId());

    }
//On receiving ACK , s updates its Membership Table with the received table (another’s CT) in ACK. For 
//each entry <id, status> in the received table,
    else if (msg.getType() == Type.ACK){
      // merge table
      for (final Entry recEntry: msg.getTable()){
        final Id targetId = recEntry.getId();
        final Status targetNewStatus = recEntry.getStatus();
        //after terminated, s has already remove itself from membership table, so following operation
        //will not work
        if (targetNewStatus == Status.ACTIVE) { // Active
          mergeActive(targetId);
        } else if (targetNewStatus == Status.SUSPECTED) { //Suspected
          mergeSuspected(targetId);
        } else if (targetNewStatus == Status.FAILED){ //Failed
          mergeFailed(targetId);
        } else if (targetNewStatus == Status.JOIN) {
          mergeJoin(targetId);
        }
      }
      // update the status of the sender
      mergeActive(msg.getSenderId());
      receivedIds.offer(msg.getSenderId());
      //decrementAndGet 自减
      if (stopFlag && countForStop.decrementAndGet() == 0) {
        LOG.log(Level.INFO, "Dissemination for termination finished. Stopping a server");
        Server.stopped = true;
      }
    }
  }

  @Override
  public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
    LOG.log(Level.FINER, "Exception is throw in inbound channel {0}", cause);
    ctx.close();
  }
}
