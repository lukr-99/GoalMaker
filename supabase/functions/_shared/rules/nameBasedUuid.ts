/**
 * A UUID version 5 (RFC 9562): SHA-1 of the namespace's 16 bytes followed by the name in UTF-8, the
 * same ids the apps' NameBasedUuid makes, so rows named this way merge across devices.
 */
export async function nameBasedUuid(namespace: string, name: string): Promise<string> {
  const space = hexBytes(namespace.replaceAll("-", ""));
  const text = new TextEncoder().encode(name);
  const input = new Uint8Array(space.length + text.length);
  input.set(space);
  input.set(text, space.length);
  const hash = new Uint8Array(await crypto.subtle.digest("SHA-1", input)).slice(0, 16);
  hash[6] = (hash[6] & 0x0f) | 0x50;
  hash[8] = (hash[8] & 0x3f) | 0x80;
  const hex = [...hash].map((byte) => byte.toString(16).padStart(2, "0")).join("");
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

function hexBytes(hex: string): Uint8Array {
  if (!/^[0-9a-fA-F]{32}$/.test(hex)) throw new Error("namespace must be a UUID");
  return Uint8Array.from({ length: 16 }, (_, index) => parseInt(hex.slice(index * 2, index * 2 + 2), 16));
}
