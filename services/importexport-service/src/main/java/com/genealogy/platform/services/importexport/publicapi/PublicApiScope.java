package com.genealogy.platform.services.importexport.publicapi;

/**
 * Closed-set scopes required by the public API. Mirrors
 * <code>contracts/importexport/public-api-policy.yaml</code>
 * <code>publicApiScopes</code>.
 */
public enum PublicApiScope {
  PUBLIC_READ_BASIC("public.read.basic"),
  PUBLIC_READ_LIVING("public.read.living"),
  PUBLIC_READ_MEDIA("public.read.media"),
  PUBLIC_READ_TREE("public.read.tree"),
  PUBLIC_READ_ALBUM("public.read.album"),
  PUBLIC_WRITE_TOKEN("public.write.token"),
  ADMIN_READ_ABUSE("admin.read.abuse");

  private final String wire;

  PublicApiScope(String wire) {
    this.wire = wire;
  }

  public String wire() {
    return wire;
  }

  public static PublicApiScope fromWire(String wire) {
    if (wire == null) {
      throw new IllegalArgumentException("publicApiScope MUST NOT be null");
    }
    for (PublicApiScope s : values()) {
      if (s.wire.equals(wire)) {
        return s;
      }
    }
    throw new IllegalArgumentException(
        "publicApiScope MUST be one of "
            + java.util.Arrays.toString(values())
            + " (got "
            + wire
            + ")");
  }
}