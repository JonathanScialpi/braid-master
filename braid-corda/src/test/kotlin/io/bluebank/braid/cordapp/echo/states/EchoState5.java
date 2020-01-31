package io.bluebank.braid.cordapp.echo.states;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import net.corda.core.identity.Party;

/**
 * This explores whether the annotation is carried over onto the
 * net.corda.core.identity.Party class, or only onto the Party1 class.
 */
public class EchoState5 {
  @Parameter(description = "The message to be echoed")
  public String message;
  @Schema(description = "The payload sent by the responder in the response flow")
  public Integer numberOfEchos;
  @Schema(description = "The initiator of the message")
  public Party1 sender;
  @Schema(description = "The responder which echoes the message")
  public Party responder;
}
