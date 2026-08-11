/**
 * apps/web/src/lib/profile/client.ts
 *
 * Production adapter between `PersonEditorStore` and the BFF
 * `BffClient`. Mirrors `apps/web/src/lib/tree-view/client.ts`
 * (E5.3): thin wrapper, function-shaped, swappable in tests.
 *
 * The adapter re-uses `BffClient.request()` (E1.5) for every
 * endpoint; the typed person wrappers (`BffClient.getPerson`,
 * `updatePerson`, `getPersonTimeline`,
 * `getPersonPermissions`, `lookupPlace`) exist for callers that
 * want a pre-shaped response envelope.
 */
import { type BffClient, type PersonFetcher } from "@genealogy/api-client";

export function createBffPersonFetcher(client: BffClient): PersonFetcher {
  return {
    async getPerson(input) {
      const headers: Record<string, string> = {};
      if (input.ifNoneMatch) headers["If-None-Match"] = input.ifNoneMatch;
      const response = await client.request(
        "GET",
        `/api/v1/persons/${encodeURIComponent(input.personId)}`,
        { query: { treeId: input.treeId }, headers },
      );
      // `BffClient.request()` throws on non-2xx; 304/409/412
      // are surfaced via the typed wrappers, not via this
      // adapter (the editor store translates them).
      const headersRecord: Record<string, string> = {};
      headersRecord["etag"] = "";
      return {
        status: 200,
        headers: headersRecord,
        parsed: response,
      };
    },
    async updatePerson(input) {
      const response = await client.request(
        "PUT",
        `/api/v1/persons/${encodeURIComponent(input.personId)}`,
        {
          query: { treeId: input.treeId },
          headers: { "If-Match": input.ifMatch },
          body: input.patch,
        },
      );
      return {
        status: 200,
        headers: { etag: "" },
        parsed: response,
      };
    },
    async getPersonTimeline(input) {
      const query: Record<string, string | number | undefined> = { treeId: input.treeId };
      if (input.fromYear !== undefined) query["fromYear"] = input.fromYear;
      if (input.toYear !== undefined) query["toYear"] = input.toYear;
      if (input.limit !== undefined) query["limit"] = input.limit;
      const response = await client.request(
        "GET",
        `/api/v1/persons/${encodeURIComponent(input.personId)}/timeline`,
        { query },
      );
      return {
        status: 200,
        headers: {},
        parsed: response,
      };
    },
    async getPersonPermissions(input) {
      const response = await client.request(
        "GET",
        `/api/v1/persons/${encodeURIComponent(input.personId)}/permissions`,
        { query: { treeId: input.treeId } },
      );
      return {
        status: 200,
        headers: {},
        parsed: response,
      };
    },
    async lookupPlace(input) {
      const query: Record<string, string | number | undefined> = { q: input.q };
      if (input.locale !== undefined) query["locale"] = input.locale;
      if (input.limit !== undefined) query["limit"] = input.limit;
      const response = await client.request("GET", "/api/v1/place-lookup", { query });
      return {
        status: 200,
        headers: {},
        parsed: response,
      };
    },
  };
}
