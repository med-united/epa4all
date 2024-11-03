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
        }
    });
});