sap.ui.define(['sap/uxap/BlockBase'], function (BlockBase) {
    "use strict";
    return BlockBase.extend("medunited.care.SharedBlocks.document.DocumentBlock", {
        metadata: {
            views: {
                Collapsed: {
                    viewName: "medunited.care.SharedBlocks.document.DocumentBlockCollapsed",
                    type: "XML"
                },
                Expanded: {
                    viewName: "medunited.care.SharedBlocks.document.DocumentBlockExpanded",
                    type: "XML"
                }
            }
        }
    });
}, true);