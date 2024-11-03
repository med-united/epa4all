// Provides control medunited.care.search.MedicationSearchProvider.
sap.ui.define([
	'sap/ui/core/search/SearchProvider',
	"sap/base/Log",
	"sap/base/security/encodeURL",
	"sap/ui/thirdparty/jquery",
	'sap/ui/core/library' // ensure that required DataTypes are available
],
	function(SearchProvider, Log, encodeURL, jQuery) {
	"use strict";



	/**
	 * Constructor for a new search/MedicationSearchProvider.
	 *
	 * @param {string} [sId] id for the new control, generated automatically if no id is given
	 * @param {object} [mSettings] initial settings for the new control
	 *
	 * @class
	 * A SearchProvider which uses the OpenSearch protocol (either JSON or XML).
	 * @extends sap.ui.core.search.SearchProvider
	 * @version ${version}
	 *
	 * @public
	 * @alias medunited.care.search.MedicationSearchProvider
	 * @ui5-metamodel This control/element also will be described in the UI5 (legacy) designtime metamodel
	 */
	var MedicationSearchProvider = SearchProvider.extend("medunited.care.search.MedicationSearchProvider", /** @lends sap.ui.core.search.MedicationSearchProvider.prototype */ { metadata : {

		library : "sap.ui.core",
		properties : {

			/**
			 * The URL for suggestions of the search provider. As placeholder for the concrete search queries '{searchTerms}' must be used. For cross domain requests maybe a proxy must be used.
			 */
			suggestUrl : {type : "sap.ui.core.URI", group : "Misc", defaultValue : null}
		}
	}});


	/**
	 * Call this function to get suggest values from the search provider.
	 * The given callback function is called with the suggest value (type 'string', 1st parameter)
	 * and an array of the suggestions (type '[string]', 2nd parameter).
	 *
	 * @param {string} sValue The value for which suggestions are requested.
	 * @param {function} fCallback The callback function which is called when the suggestions are available.
	 * @type void
	 * @public
	 */
	MedicationSearchProvider.prototype.suggest = function(sValue, fCallback) {
		var sUrl = this.getSuggestUrl();
		if (!sUrl) {
			return;
		}
		sUrl = sUrl.replace("{searchTerms}", encodeURL(sValue));

		var fSuccess;
		
        //Docu: http://www.opensearch.org/Specifications/OpenSearch/Extensions/Suggestions/1.1#Response_format
        fSuccess = function(data){
            fCallback(sValue, data.results);
        };

		jQuery.ajax({
			url: sUrl,
			dataType: "json",
			success: fSuccess,
			error: function(XMLHttpRequest, textStatus, errorThrown) {
				Log.fatal("The following problem occurred: " + textStatus, XMLHttpRequest.responseText + ","
						+ XMLHttpRequest.status);
			}
		});
	};


	return MedicationSearchProvider;

});