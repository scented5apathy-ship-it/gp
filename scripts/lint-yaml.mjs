#!/usr/bin/env node
/**
 * scripts/lint-yaml.mjs
 *
 * Minimal block-YAML parser shared by the search/authorized-search /
 * public-projection / benchmark-evolution-gate lint scripts. Loads
 * the file, strips comments, handles block sequences / mappings /
 * inline lists / multiline `|` / `>` scalars and returns a plain
 * JavaScript object. Not a full YAML 1.2 implementation — just
 * enough for the contract files we author.
 *
 * Exported as `loadYaml(text)`.
 */
export function loadYaml(text) {
  const lines = text.split(/\r?\n/);
  const root = {};
  const stack = [{ indent: -1, container: root, isArray: false }];

  const stripComment = (line) => {
    const idx = line.indexOf("#");
    if (idx < 0) return line;
    let inString = false;
    let quote = null;
    for (let i = 0; i < idx; i += 1) {
      const c = line[i];
      if (inString) {
        if (c === "\\") {
          i += 1;
          continue;
        }
        if (c === quote) inString = false;
        continue;
      }
      if (c === '"' || c === "'") {
        inString = true;
        quote = c;
      }
    }
    if (inString) return line;
    return line.slice(0, idx);
  };

  const parseInlineList = (raw) => {
    const inner = raw.trim();
    if (!inner.startsWith("[") || !inner.endsWith("]")) return undefined;
    const body = inner.slice(1, -1).trim();
    if (!body) return [];
    return body
      .split(",")
      .map((p) => p.trim())
      .filter((p) => p.length > 0)
      .map((p) => p.replace(/^['"]|['"]$/g, ""));
  };

  const coerce = (raw) => {
    const trimmed = raw.trim();
    if (trimmed === "") return "";
    if (trimmed === "true") return true;
    if (trimmed === "false") return false;
    if (trimmed === "null" || trimmed === "~") return null;
    const inline = parseInlineList(trimmed);
    if (inline !== undefined) return inline;
    if (/^-?\d+$/.test(trimmed)) return Number.parseInt(trimmed, 10);
    if (/^-?\d+\.\d+$/.test(trimmed)) return Number.parseFloat(trimmed);
    if (
      (trimmed.startsWith('"') && trimmed.endsWith('"')) ||
      (trimmed.startsWith("'") && trimmed.endsWith("'"))
    ) {
      return trimmed.slice(1, -1);
    }
    return trimmed;
  };

  for (let raw of lines) {
    const stripped = stripComment(raw);
    if (!stripped.trim()) continue;
    const indentMatch = /^( *)(.*)$/.exec(stripped);
    const indent = indentMatch[1].length;
    const body = indentMatch[2];

    if (body !== "|" && body !== ">" && stack.length > 0) {
      const top = stack[stack.length - 1];
      if (
        !Array.isArray(top.container) &&
        top.pendingBlockScalar &&
        indent >= top.pendingBlockScalar
      ) {
        top.container[top.pendingBlockScalarKey] = top.container[top.pendingBlockScalarKey]
          ? `${top.container[top.pendingBlockScalarKey]}\n${stripped.trim()}`
          : stripped.trim();
        continue;
      }
      if (top.pendingBlockScalar && indent <= top.pendingBlockScalar) {
        top.pendingBlockScalar = 0;
        top.pendingBlockScalarKey = null;
      }
    }

    while (stack.length > 1) {
      const top = stack[stack.length - 1];
      if (indent < top.indent) {
        top.pendingBlockScalar = 0;
        top.pendingBlockScalarKey = null;
        stack.pop();
      } else break;
    }
    const frame = stack[stack.length - 1];
    if (body.startsWith("- ")) {
      if (!Array.isArray(frame.container)) {
        // eslint-disable-next-line no-console
        console.error(`YAML: unexpected sequence at indent ${indent}`);
        continue;
      }
      const item = body.slice(2).trim();
      if (item.length === 0) {
        const child = {};
        frame.container.push(child);
        stack.push({ indent: indent + 2, container: child, isArray: false });
        continue;
      }
      const colonIdx = item.indexOf(":");
      if (colonIdx < 0) {
        frame.container.push(coerce(item));
        continue;
      }
      const key = item.slice(0, colonIdx).trim();
      const rest = item.slice(colonIdx + 1).trim();
      const child = {};
      child[key] = rest === "" ? null : coerce(rest);
      frame.container.push(child);
      stack.push({ indent: indent + 2, container: child, isArray: false });
      continue;
    }
    const colonIdx = body.indexOf(":");
    if (colonIdx < 0) {
      // eslint-disable-next-line no-console
      console.error(`YAML: invalid line '${body}'`);
      continue;
    }
    const key = body.slice(0, colonIdx).trim();
    const rest = body.slice(colonIdx + 1).trim();
    if (!frame.container || Array.isArray(frame.container)) {
      // eslint-disable-next-line no-console
      console.error(`YAML: cannot add key '${key}' to non-mapping frame`);
      continue;
    }
    if (rest === "|" || rest === ">") {
      frame.container[key] = "";
      frame.pendingBlockScalar = indent + 2;
      frame.pendingBlockScalarKey = key;
      continue;
    }
    if (rest === "" || rest === null) {
      const nextIdx = lines.indexOf(raw) + 1;
      let nextMeaningful = "";
      for (let i = nextIdx; i < lines.length; i += 1) {
        const cand = stripComment(lines[i]);
        if (cand.trim().length === 0) continue;
        nextMeaningful = cand;
        break;
      }
      const nextIndent = /^( *)/.exec(nextMeaningful)[1].length;
      if (nextMeaningful.trim().startsWith("- ") && nextIndent > indent) {
        const arr = [];
        frame.container[key] = arr;
        stack.push({ indent: nextIndent, container: arr, isArray: true });
      } else {
        const child = {};
        frame.container[key] = child;
        stack.push({ indent: nextIndent, container: child, isArray: false });
      }
      continue;
    }
    frame.container[key] = coerce(rest);
  }
  return root;
}

export function asArray(value) {
  if (value === null || value === undefined) return [];
  if (Array.isArray(value)) return value;
  if (typeof value === "object") return Object.values(value);
  return [value];
}

/**
 * Validate that the actual array exactly equals the expected closed
 * set (order-independent, no duplicates). Reports via the supplied
 * ok / fail callbacks so the caller owns the violations list.
 */
export function assertClosedSet(name, expected, actual, label, ok, fail) {
  const expectedSorted = [...expected].sort().join(",");
  const actualSorted = [...actual].sort().join(",");
  if (expectedSorted !== actualSorted) {
    fail(
      `${label || name}: closed-set mismatch.\n     expected: ${expectedSorted}\n     actual:   ${actualSorted}`,
    );
    return;
  }
  if (expected.length === 0) {
    fail(`${label || name}: empty closed-set is forbidden`);
    return;
  }
  ok(`${label || name} (${actual.length} values)`);
}

/**
 * Validate a state machine block:
 *  - terminal statuses MUST have empty transitions;
 *  - non-terminal statuses MUST have at least one transition;
 *  - every status declared in the matrix MUST appear in expectedStatuses;
 *  - no status outside expectedStatuses may exist;
 *  - every status must be reachable from initialStatus via BFS
 *    (terminal + initial are excluded from reachability scan);
 *  - initialStatus MUST match the supplied initial parameter.
 */
export function assertStateMatrix(label, matrix, expectedStatuses, initialStatus, ok, fail) {
  if (!matrix || typeof matrix !== "object") {
    fail(`${label}: state matrix missing`);
    return;
  }
  const statuses = asArray(matrix.statuses);
  if (statuses.length === 0) {
    fail(`${label}: state matrix is empty`);
    return;
  }
  const seen = new Set();
  const reachable = new Set([initialStatus]);
  const frontier = [initialStatus];
  for (const entry of statuses) {
    if (!entry || typeof entry !== "object") {
      fail(`${label}: invalid status entry ${JSON.stringify(entry)}`);
      continue;
    }
    const status = entry.status;
    if (!status) {
      fail(`${label}: status entry missing 'status' field`);
      continue;
    }
    seen.add(status);
    const transitions = asArray(entry.transitions);
    if (!Array.isArray(transitions)) {
      fail(`${label}: ${status}.transitions must be an array`);
      continue;
    }
    if (entry.terminal === true) {
      if (transitions.length !== 0) {
        fail(`${label}: terminal status ${status} MUST have empty transitions (got ${JSON.stringify(transitions)})`);
      } else {
        ok(`${label}: terminal status ${status} has empty transitions`);
      }
    } else {
      if (transitions.length === 0) {
        fail(`${label}: non-terminal status ${status} MUST declare at least one transition`);
      }
    }
    for (const t of transitions) {
      if (typeof t !== "string") {
        fail(`${label}: ${status} transition ${JSON.stringify(t)} is not a string`);
      }
    }
    if (entry.terminal !== true) {
      for (const t of transitions) reachable.add(t);
    }
  }
  if (matrix.initialStatus !== initialStatus) {
    fail(`${label}: initialStatus MUST equal ${initialStatus} (got ${matrix.initialStatus})`);
  }
  for (const s of expectedStatuses) {
    if (!seen.has(s)) {
      fail(`${label}: expected status ${s} missing`);
    }
  }
  for (const s of seen) {
    if (!expectedStatuses.includes(s)) {
      fail(`${label}: unexpected status ${s} in matrix`);
    }
  }
  for (const s of seen) {
    if (!reachable.has(s) && s !== initialStatus) {
      fail(`${label}: status ${s} is unreachable from ${initialStatus}`);
    }
  }
  ok(`${label}: ${seen.size} statuses, ${expectedStatuses.length - seen.size} missing`);
}