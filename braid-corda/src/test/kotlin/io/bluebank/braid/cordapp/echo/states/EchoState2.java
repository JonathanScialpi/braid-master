package io.bluebank.braid.cordapp.echo.states;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * This is like EchoState2
 * except it tests annotating property-getter-methods
 * instead of annotating fields.
 */
public class EchoState2 {
  private String message;
  private Integer numberOfEchos;
  private Party1 sender;
  private Party1 responder;

  @Parameter(description = "The message to be echoed")
  public String getMessage() {
    return message;
  }

  @Schema(description = "The payload sent by the responder in the response flow")
  public Integer getNumberOfEchos() {
    return numberOfEchos;
  }

  @Schema(description = "The initiator of the message")
  public Party1 getSender() {
    return sender;
  }

  @Schema(description = "The responder which echoes the message")
  public Party1 getResponder() {
    return responder;
  }
}
