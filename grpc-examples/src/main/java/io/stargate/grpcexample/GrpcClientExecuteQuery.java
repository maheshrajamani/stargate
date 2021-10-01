package io.stargate.grpcexample;

import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.stargate.grpc.StargateBearerToken;
import io.stargate.proto.QueryOuterClass;
import io.stargate.proto.StargateGrpc;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * This is a runnable code example explained in detail in the README.md file in this repo. To run
 * this example, you need to have a running Stargate instance. On default, it assumes that the
 * Stargate instance is running on localhost:8090. If you need to change it, please adapt the {@link
 * GrpcClientExecuteQuery#STARGATE_HOST} and/or {@link GrpcClientExecuteQuery#STARGATE_GRPC_PORT}.
 * Lastly, it requires the Stargate Token to perform auth logic. Please set it in the: {@link
 * GrpcClientExecuteQuery#STARGATE_TOKEN}.
 *
 * <p>It executes:
 *
 * <ol>
 *   <li>DDL to setup keyspace and table
 *   <li>Blocking insert and data retrieval for single element
 *   <li>Blocking insert and data retrieval for batch of elements
 *   <li>Async query
 * </ol>
 *
 * This example can run as is for the local setup. If you want to run it using the astra database
 * (https://astra.datastax.com/) You need to adapt couple of settings:
 *
 * <ol>
 *   <li>Change the {@link GrpcClientExecuteQuery#STARGATE_TOKEN} to the astra token
 *       (https://docs.datastax.com/en/astra/docs/manage-application-tokens.html).
 *   <li>Adapt the {@link GrpcClientExecuteQuery#STARGATE_HOST} according to the {@code
 *       "{$ASTRA_DB_ID}-{ASTRA_DB_REGION}.apps.astra.datastax.com"}.
 *   <li>Change the {@link GrpcClientExecuteQuery#LOCAL_RUN} to prevent new keyspace creation.
 *   <li>Change the {@link GrpcClientExecuteQuery#STARGATE_GRPC_PORT} to 443.
 *   <li>Change the {@link GrpcClientExecuteQuery#KS} to ASTRA_DB_KEYSPACE.
 * </ol>
 */
public class GrpcClientExecuteQuery {

  private final StargateGrpc.StargateBlockingStub blockingStub;
  private final StargateGrpc.StargateStub asyncStub;

  private static final String STARGATE_TOKEN = ""; // you need to set the token
  private static final String STARGATE_HOST = "localhost";;
  //  when running on astra
  //  private static final String STARGATE_HOST =
  //  "{$ASTRA_DB_ID}-{ASTRA_DB_REGION}.apps.astra.datastax.com";

  // change to false when running on Astra
  private static final boolean LOCAL_RUN = false;

  private static final int STARGATE_GRPC_PORT = 8090;
  // when running on astra
  // private static final int STARGATE_GRPC_PORT = 443;
  private static final String KS = "grpc"; // when running on astra, change to the ASTRA_DB_KEYSPACE
  private static final String KS_AND_TABLE = KS + ".test";

  /** Runner of the GrpcClientExample */
  public static void main(String[] args)
      throws InvalidProtocolBufferException, InterruptedException {
    GrpcClientExecuteQuery grpcClientExecuteQuery = new GrpcClientExecuteQuery();
    grpcClientExecuteQuery.prepareSchema();
    grpcClientExecuteQuery.executeSingleQuery();
    grpcClientExecuteQuery.executeBatchQueries();
    grpcClientExecuteQuery.executeAsyncQueries();
  }

  private void prepareSchema() {

    if (LOCAL_RUN) {
      blockingStub.executeQuery(
          QueryOuterClass.Query.newBuilder()
              .setCql(
                  "CREATE KEYSPACE IF NOT EXISTS "
                      + KS
                      + " WITH REPLICATION = {'class':'SimpleStrategy', 'replication_factor':'1'};")
              .build());
    }

    blockingStub.executeQuery(
        QueryOuterClass.Query.newBuilder()
            .setCql(
                "CREATE TABLE IF NOT EXISTS "
                    + KS_AND_TABLE
                    + " (k text, v int, PRIMARY KEY(k, v))")
            .build());
  }

  /** Construct client for accessing StargateGrpc server using a new channel. */
  public GrpcClientExecuteQuery() {
    ManagedChannel channel = createChannel(STARGATE_HOST, STARGATE_GRPC_PORT);
    // blocking stub version
    blockingStub =
        StargateGrpc.newBlockingStub(channel)
            .withDeadlineAfter(
                10, TimeUnit.SECONDS) // deadline is > 5 because we execute DDL queries
            .withCallCredentials(new StargateBearerToken(STARGATE_TOKEN));
    // async stub version
    asyncStub =
        StargateGrpc.newStub(channel)
            .withDeadlineAfter(10, TimeUnit.SECONDS)
            .withCallCredentials(new StargateBearerToken(STARGATE_TOKEN));
  }

  public void executeSingleQuery() throws InvalidProtocolBufferException {
    // insert
    QueryOuterClass.Response response =
        blockingStub.executeQuery(
            QueryOuterClass.Query.newBuilder()
                .setCql("INSERT INTO " + KS_AND_TABLE + " (k, v) VALUES ('a', 1)")
                .build());
    System.out.println(response);
    // retrieve
    response =
        blockingStub.executeQuery(
            QueryOuterClass.Query.newBuilder().setCql("SELECT k, v FROM " + KS_AND_TABLE).build());

    QueryOuterClass.ResultSet rs =
        response.getResultSet().getData().unpack(QueryOuterClass.ResultSet.class);

    System.out.println(
        "k = " + rs.getRows(0).getValues(0).getString()); // it will return value for k = "a"
    System.out.println(
        "v = " + rs.getRows(0).getValues(1).getInt()); // it will return value for v = 1
  }

  private void executeBatchQueries() throws InvalidProtocolBufferException {
    // batch insert
    QueryOuterClass.Response response =
        blockingStub.executeBatch(
            QueryOuterClass.Batch.newBuilder()
                .addQueries(
                    QueryOuterClass.BatchQuery.newBuilder()
                        .setCql("INSERT INTO " + KS_AND_TABLE + " (k, v) VALUES ('a', 1)")
                        .build())
                .addQueries(
                    QueryOuterClass.BatchQuery.newBuilder()
                        .setCql("INSERT INTO " + KS_AND_TABLE + " (k, v) VALUES ('b', 2)")
                        .build())
                .build());
    System.out.println(response);
    // retrieve
    response =
        blockingStub.executeQuery(
            QueryOuterClass.Query.newBuilder().setCql("SELECT k, v FROM " + KS_AND_TABLE).build());
    QueryOuterClass.ResultSet rs =
        response.getResultSet().getData().unpack(QueryOuterClass.ResultSet.class);
    // iterate over all (two) results
    for (QueryOuterClass.Row row : rs.getRowsList()) {
      System.out.println(row.getValuesList());
    }
  }

  private void executeAsyncQueries() throws InterruptedException {

    CountDownLatch responseRetrieved = new CountDownLatch(1);
    StreamObserver<QueryOuterClass.Response> streamObserver =
        new StreamObserver<QueryOuterClass.Response>() {
          @Override
          public void onNext(QueryOuterClass.Response response) {
            try {
              System.out.println(
                  "Async response: "
                      + response.getResultSet().getData().unpack(QueryOuterClass.ResultSet.class));
              responseRetrieved.countDown();
            } catch (InvalidProtocolBufferException e) {
              throw new RuntimeException(e);
            }
          }

          @Override
          public void onError(Throwable throwable) {
            System.out.println("Error: " + throwable);
          }

          @Override
          public void onCompleted() {
            // close resources, finish processing
            System.out.println("completed");
          }
        };

    asyncStub.executeQuery(
        QueryOuterClass.Query.newBuilder().setCql("SELECT k, v FROM " + KS_AND_TABLE).build(),
        streamObserver);

    // wait until the result arrive
    responseRetrieved.await();
  }

  public ManagedChannel createChannel(String host, int port) {
    return ManagedChannelBuilder.forAddress(host, port).build();
  }
}