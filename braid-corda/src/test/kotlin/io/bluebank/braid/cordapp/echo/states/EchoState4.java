package io.bluebank.braid.cordapp.echo.states;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Like EchoState3 this is another test using `allOf` except this time using it
 * more-or-less the way it's apparently supposed to be used in a Schema annotation
 * https://github.com/swagger-api/swagger-core/blob/master/modules/swagger-core/src/test/java/io/swagger/v3/core/oas/models/composition/Thing1.java
 */
@Schema(description = "The responder which echoes the message", allOf = {Party1.class})
public class EchoState4 extends Party1 {
  @Parameter(description = "The message to be echoed")
  public String message;
  @Schema(description = "The payload sent by the responder in the response flow")
  public Integer numberOfEchos;
  public Party1 sender;
  public Party1 responder;
}
