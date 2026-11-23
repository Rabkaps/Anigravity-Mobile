export interface UiTreeNode {
    index: number;
    id?: string;
    text?: string;
    contentDesc?: string;
    className: string;
    packageName?: string;
    clickable: boolean;
    scrollable: boolean;
    enabled: boolean;
    checked?: boolean;
    bounds: {
        left: number;
        top: number;
        right: number;
        bottom: number;
    };
    center: {
        x: number;
        y: number;
    };
}
/**
 * Parses `uiautomator dump` XML into a compact list of actionable or visible UI nodes
 * so LLM context isn't wasted on empty layout wrappers.
 */
export declare function parseUiTreeXml(xml: string): UiTreeNode[];
