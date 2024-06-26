
package wiremock.grpc;


import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.wiremock.grpc.dsl.WireMockGrpc.*;
import static org.wiremock.grpc.dsl.WireMockGrpc.method;

import com.example.grpc.GreetingServiceGrpc;
import com.example.grpc.HelloRequest;
import com.example.grpc.HelloResponse;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.wiremock.grpc.GrpcExtensionFactory;
import org.wiremock.grpc.dsl.WireMockGrpcService;
import wiremock.grpc.client.GreetingsClient;

public class GrpcTest {

  WireMockGrpcService GreetingService;
  ManagedChannel channel;
  GreetingsClient greetingsClient;


  @RegisterExtension
  public static WireMockExtension wm =
          WireMockExtension.newInstance()
                  .options(
                          wireMockConfig()
                                  .dynamicPort()
                                  .withRootDirectory("src/test/resources/wiremock")
                                  .extensions(new GrpcExtensionFactory())
                  )
                  .build();


  @BeforeEach
  void init() {
    GreetingService =
            new WireMockGrpcService(new WireMock(wm.getPort()), GreetingServiceGrpc.SERVICE_NAME);

    channel = ManagedChannelBuilder.forAddress("localhost", wm.getPort()).usePlaintext().build();
    greetingsClient = new GreetingsClient(channel);
  }


  @AfterEach
  void tearDown() {
    channel.shutdown();
  }


  @Test
  void dynamic_response_via_JSON() {
    GreetingService.stubFor(
            method("greeting")
                    .willReturn(
                            jsonTemplate(
                                    "{\n"
                                            + "    \"greeting\": \"Hello {{jsonPath request.body '$.name'}}\"\n"
                                            + "}")));

    String greeting = greetingsClient.greet("Tom");

    assertThat(greeting, is("Hello Tom"));
    System.out.println("Hello Tom");

  }




  @Test
  void response_from_message() {
    GreetingService.stubFor(
            method("greeting")
                    .willReturn(message(HelloResponse.newBuilder().setGreeting("Hi Tom from object"))));

    String greeting = greetingsClient.greet("This is Niha");

    assertThat(greeting, is("Hi Tom from object"));
    System.out.println("Hi Tom this is Niha");
  }


  @Test
  void non_OK_status() {
    GreetingService.stubFor(
            method("greeting").willReturn(Status.FAILED_PRECONDITION, "Failed some blah prerequisite"));

    StatusRuntimeException exception =
            assertThrows(StatusRuntimeException.class, () -> greetingsClient.greet("Whatever"));
    assertThat(exception.getMessage(), is("FAILED_PRECONDITION: Failed some blah prerequisite"));
  }

  @Test
  void streaming_request_unary_response() {
    GreetingService.stubFor(
            method("manyGreetingsOneReply")
                    .withRequestMessage(equalToMessage(HelloRequest.newBuilder().setName("Rob").build()))
                    .willReturn(message(HelloResponse.newBuilder().setGreeting("Hi Rob"))));

    assertThat(greetingsClient.manyGreetingsOneReply("Tom", "Uri", "Rob", "Mark"), is("Hi Rob"));
  }

  @Test
  void unary_request_streaming_response() {
    GreetingService.stubFor(
            method("oneGreetingManyReplies")
                    .willReturn(message(HelloResponse.newBuilder().setGreeting("Hi Tom"))));

    assertThat(greetingsClient.oneGreetingManyReplies("Tom"), hasItem("Hi Tom"));
  }


}