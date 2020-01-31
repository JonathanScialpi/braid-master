package io.bluebank.braid.core.synth;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface SeeAlso {
  Class value();
}
