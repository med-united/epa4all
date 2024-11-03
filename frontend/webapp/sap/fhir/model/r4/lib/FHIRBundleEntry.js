/*!
 * SAP SE
 * (c) Copyright 2009-2022 SAP SE or an SAP affiliate company.
 * Licensed under the Apache License, Version 2.0 - see LICENSE.txt.
 */
sap.ui.define([],function(){"use strict";var e=function(e,t,u){this._sFullUrl=e;this._oResource=t;this._oRequest=u};e.prototype.getBundleEntryData=function(){var e={};e.fullUrl=this._sFullUrl?this._sFullUrl:undefined;e.resource=this._oResource?this._oResource:undefined;e.request=this._oRequest.getBundleRequestData();return e};e.prototype.getRequest=function(){return this._oRequest};e.prototype.getResource=function(){return this._oResource};e.prototype.getFullUrl=function(){return this._sFullUrl};return e});