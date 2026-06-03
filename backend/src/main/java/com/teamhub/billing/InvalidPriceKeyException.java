package com.teamhub.billing;

import com.teamhub.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Thrown when the caller supplies a price key that is not one of the four known plan keys. */
public class InvalidPriceKeyException extends DomainException {

  public static final String CODE = "INVALID_PRICE_KEY";

  public InvalidPriceKeyException(String priceKey) {
    super(CODE, HttpStatus.BAD_REQUEST, "Unknown price key: '" + priceKey + "'");
  }
}
