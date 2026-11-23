const ATTR_REGEX = /([a-zA-Z0-9_:-]+)="([^"]*)"/g;
const BOUNDS_REGEX = /^\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]$/;
function decodeXmlEntities(value) {
    return value
        .replaceAll("&quot;", '"')
        .replaceAll("&apos;", "'")
        .replaceAll("&lt;", "<")
        .replaceAll("&gt;", ">")
        .replaceAll("&amp;", "&");
}
function parseAttributes(rawAttrBlock) {
    const attrs = {};
    ATTR_REGEX.lastIndex = 0;
    let match;
    while ((match = ATTR_REGEX.exec(rawAttrBlock)) !== null) {
        attrs[match[1]] = decodeXmlEntities(match[2]);
    }
    return attrs;
}
/**
 * Parses `uiautomator dump` XML into a compact list of actionable or visible UI nodes
 * so LLM context isn't wasted on empty layout wrappers.
 */
export function parseUiTreeXml(xml) {
    const nodes = [];
    const nodeTagRegex = /<node\s+([^>]+?)\s*\/?>/g;
    let match;
    let index = 0;
    while ((match = nodeTagRegex.exec(xml)) !== null) {
        const attrs = parseAttributes(match[1]);
        const boundsStr = attrs["bounds"] ?? "";
        const boundsMatch = BOUNDS_REGEX.exec(boundsStr);
        if (!boundsMatch)
            continue;
        const left = Number(boundsMatch[1]);
        const top = Number(boundsMatch[2]);
        const right = Number(boundsMatch[3]);
        const bottom = Number(boundsMatch[4]);
        if (right <= left || bottom <= top)
            continue;
        const text = attrs["text"]?.trim() || undefined;
        const contentDesc = attrs["content-desc"]?.trim() || undefined;
        const resourceId = attrs["resource-id"]?.trim() || undefined;
        const clickable = attrs["clickable"] === "true";
        const scrollable = attrs["scrollable"] === "true";
        const enabled = attrs["enabled"] !== "false";
        const checked = attrs["checkable"] === "true" ? attrs["checked"] === "true" : undefined;
        // Keep only nodes that convey content, have an ID, or can be interacted with.
        if (!text && !contentDesc && !resourceId && !clickable && !scrollable) {
            continue;
        }
        const shortClass = (attrs["class"] || "View").split(".").pop() ?? "View";
        nodes.push({
            index: index++,
            ...(resourceId ? { id: resourceId } : {}),
            ...(text ? { text } : {}),
            ...(contentDesc ? { contentDesc } : {}),
            className: shortClass,
            ...(attrs["package"] ? { packageName: attrs["package"] } : {}),
            clickable,
            scrollable,
            enabled,
            ...(checked !== undefined ? { checked } : {}),
            bounds: { left, top, right, bottom },
            center: {
                x: Math.round((left + right) / 2),
                y: Math.round((top + bottom) / 2),
            },
        });
    }
    return nodes;
}
