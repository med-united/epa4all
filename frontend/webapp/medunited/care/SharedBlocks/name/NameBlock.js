sap.ui.define(['sap/uxap/BlockBase'], function (BlockBase) {
	"use strict";
	return BlockBase.extend("medunited.care.SharedBlocks.name.NameBlock", {
		metadata: {
			views: {
				Collapsed: {
					viewName: "medunited.care.SharedBlocks.name.NameBlockCollapsed",
					type: "XML"
				},
				Expanded: {
					viewName: "medunited.care.SharedBlocks.name.NameBlockExpanded",
					type: "XML"
				}
			}
		}
	});
}, true);
