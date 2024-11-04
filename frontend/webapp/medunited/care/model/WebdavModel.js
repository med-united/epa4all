sap.ui.define([
	"sap/ui/model/xml/XMLModel"
], function(XMLModel) {

	"use strict";

	var WebdavModel = XMLModel.extend("medunited.care.model.WebdavModel", {
        constructor : function(sServiceUrl, mParameters) {
			XMLModel.apply(this);
			if (!sServiceUrl) {
				throw new Error("Missing service root URL");
			}
			sServiceUrl = sServiceUrl.slice(-1) === "/" ?  sServiceUrl.slice(0, -1) : sServiceUrl;
			this.oServiceUrl = new URI(sServiceUrl);
			this.sServiceUrl = this.oServiceUrl.query("").toString();
			// Set default namespace
			// Relative bindings: {D:propstat/D:prop/D:displayname} cause an issue with the XML parser
			// Absolute ones work {/D:response}
			this.setNameSpace("DAV:");
			this.setNameSpace("http://ws.gematik.de/fa/vsdm/vsd/v5.2", "vsdm")
			this._setupData();
        },
        _setupData: function() {
			let me = this;
            fetch(this.sServiceUrl, {
                "method": "PROPFIND",
                "headers": {
                    "Depth": "1"
                }
            })
			.then(o => o.text())
			.then(xml => me.setXML(xml) );
        },
		loadFileForContext: function(sPath, sFileWithPath) {
			let me = this;
			let decoder = new TextDecoder('iso-8859-1');
            fetch(this.sServiceUrl+sFileWithPath)
			.then(o => o.arrayBuffer())
			.then(buf => decoder.decode(buf))
			.then(str => new window.DOMParser().parseFromString(str, "text/xml"))
			.then(xml => {
				let oNodesForImport = me.getData().importNode(xml.documentElement,true);
				let oNode = me._getObject(sPath)[0];
				oNode.appendChild(oNodesForImport);
				me.updateBindings();
			});
		}
    });
});