package io.bluebank.braid.cordapp.echo.states;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * This is a test of using trying to use `allOf` is a class Schema to avoid the problem
 * associated with the composite type being referenced directly using `$ref`.
 */
public class EchoState3 {
  @Parameter(description = "The message to be echoed")
  public String message;
  @Schema(description = "The payload sent by the responder in the response flow")
  public Integer numberOfEchos;
  @Schema(description = "The initiator of the message", allOf = {Party1.class})
  public Party1 sender;
  @Schema(description = "The responder which echoes the message", allOf = {Party1.class})
  public Party1 responder;
}
