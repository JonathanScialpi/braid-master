package io.bluebank.braid.cordapp.echo.states;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * This tests annotations on fields:
 * <p>
 * 1. A @Parameter annotation (is legal but isn't preserved in the Swagger model)
 * 2. A @Schema annotation on a simple/primitive type (works properly)
 * 3. A @Schema annotation on an object type (doesn't work because type is a $ref in the model)
 */
public class EchoState1 {
  @Parameter(description = "The message to be echoed")
  public String message;
  @Schema(description = "The payload sent by the responder in the response flow")
  public Integer numberOfEchos;
  @Schema(description = "The initiator of the message")
  public Party1 sender;
  @Schema(description = "The responder which echoes the message")
  public Party1 responder;
}
