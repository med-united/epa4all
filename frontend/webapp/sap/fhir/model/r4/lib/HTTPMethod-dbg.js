/*!
 * SAP SE
 * (c) Copyright 2009-2022 SAP SE or an SAP affiliate company.
 * Licensed under the Apache License, Version 2.0 - see LICENSE.txt.
 */

// Provides enumeration sap.fhir.model.r4.HTTPMethod
sap.ui.define(function() {

	"use strict";

	/**
	 * The HTTP methods which are send to the server
	 *
	 * @alias sap.fhir.model.r4.HTTPMethod
	 * @author SAP SE
	 * @enum {string}
	 * @public
	 * @since 1.0.0
	 * @version 2.3.3
	 */
	var HTTPMethod = {

		/**
		 * for deleting resources
		 * @public
		 */
		DELETE : "DELETE",
		/**
		 * for creating resources or sending a bundle
		 * @public
		 */
		POST : "POST",
		/**
		 * for updating resources
		 * @public
		 */
		PUT : "PUT",
		/**
		 * not supported
		 * @public
		 */
		PATCH : "PATCH",
		/**
		 * for requesting resources
		 * @public
		 */
		GET : "GET",
		/**
		 * for requesting latest version
		 * @public
		 */
		HEAD : "HEAD"

	};
	return HTTPMethod;
});