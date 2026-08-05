/**
 * Type-safe accessor for the OpenAPI-generated types. Each
 * generated module exports `components` (an interface) and `paths`
 * (a type). This barrel re-collects them into a single
 * `components` object so the UI can look up `components["bff"]`
 * without juggling file paths.
 *
 * Example — types for the BFF session endpoint:
 *
 *   import type { components } from "@/lib/api/types";
 *   type Session = components["bff"]["schemas"]["Session"];
 *
 * The barrel is hand-maintained because openapi-typescript emits
 * each contract as an `interface components` (i.e. a type, not a
 * value). Adding a new contract? Append it to both the import list
 * and the `components` object below — the typecheck will catch a
 * missing entry.
 */
import type { components as BffSession } from "./generated/bff__v1__session";
import type { components as CommonHeaders } from "./generated/common__headers";
import type { components as CommonPagination } from "./generated/common__pagination";
import type { components as CommonProblemDetails } from "./generated/common__problem-details";
import type { components as PublicApiEvents } from "./generated/public-api__v1__events";
import type { components as PublicApiPerson } from "./generated/public-api__v1__person";
import type { components as PublicApiTenant } from "./generated/public-api__v1__tenant";
import type { components as PublicApiTree } from "./generated/public-api__v1__tree";

export interface components {
  bff: BffSession;
  common_headers: CommonHeaders;
  common_pagination: CommonPagination;
  common_problem_details: CommonProblemDetails;
  public_api_events: PublicApiEvents;
  public_api_person: PublicApiPerson;
  public_api_tenant: PublicApiTenant;
  public_api_tree: PublicApiTree;
}

export type Paths = {
  bff: import("./generated/bff__v1__session").paths;
  common_headers: import("./generated/common__headers").paths;
  common_pagination: import("./generated/common__pagination").paths;
  common_problem_details: import("./generated/common__problem-details").paths;
  public_api_events: import("./generated/public-api__v1__events").paths;
  public_api_person: import("./generated/public-api__v1__person").paths;
  public_api_tenant: import("./generated/public-api__v1__tenant").paths;
  public_api_tree: import("./generated/public-api__v1__tree").paths;
};
