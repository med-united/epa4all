/*!
 * SAP SE
 * (c) Copyright 2009-2022 SAP SE or an SAP affiliate company.
 * Licensed under the Apache License, Version 2.0 - see LICENSE.txt.
 */
sap.ui.define([],function(){"use strict";var e=function(t,r){this._sServiceUrl=r;if(r.indexOf("http")==0||r.indexOf("https")==0){r=r.replace(/^https?\:\/\//i,"")}var s=t.indexOf(r);t=s>-1?t.substring(s+r.length):t;t=t&&t.charAt(0)!=="/"&&t.charAt(0)!=="?"?"/"+t:t;if(t.indexOf("?")>-1){this._sRelativeUrlWithQueryParameters=t;this._sRelativeUrlWithoutQueryParameters=t.substring(0,t.indexOf("?"))}else{this._sRelativeUrlWithQueryParameters=t;this._sRelativeUrlWithoutQueryParameters=t}var i=this._sRelativeUrlWithoutQueryParameters?this._sRelativeUrlWithoutQueryParameters.split("/"):undefined;this._sResourceType=i?i[1]:undefined;this._sResourceId=i&&i.length>=3&&!i[2].includes("$")?i[2]:undefined;this._sHistoryVersion=i&&i.indexOf("_history")>-1?i[i.indexOf("_history")+1]:undefined;this._mQueryParameter=e.getQueryParametersByUrl(this._sRelativeUrlWithQueryParameters);this._sCustomOperation=this._sRelativeUrlWithoutQueryParameters&&this._sRelativeUrlWithoutQueryParameters.indexOf("$")>-1?this._sRelativeUrlWithoutQueryParameters.substring(this._sRelativeUrlWithoutQueryParameters.indexOf("$"),this._sRelativeUrlWithoutQueryParameters.length):undefined};e.prototype.getRelativeUrlWithoutQueryParameters=function(){return this._sRelativeUrlWithoutQueryParameters};e.prototype.getRelativeUrlWithQueryParameters=function(){return this._sRelativeUrlWithQueryParameters};e.prototype.getResourceType=function(){return this._sResourceType};e.prototype.getResourceId=function(){return this._sResourceId};e.prototype.getQueryParameters=function(){return this._mQueryParameter};e.prototype.getHistoryVersion=function(){return this._sHistoryVersion};e.prototype.getFullUrl=function(e){if(e&&e.substring(0,4)==="http"){var t=e.indexOf("?");return e.substring(0,t>-1?t:undefined)}else if(this._sServiceUrl.charAt(0)!=="/"&&this._sResourceType&&this._sResourceId){return this._sServiceUrl+"/"+this._sResourceType+"/"+this._sResourceId}else{return undefined}};e.getQueryParametersByUrl=function(e){if(e){var t=e.indexOf("?");if(t>-1){var r=e.substring(t+1).split("&");var s={};var i;for(var u=0;u<r.length;u++){i=r[u].split("=");s[i[0]]=i[1]}return s}}return undefined};e.prototype.getCustomOperation=function(){return this._sCustomOperation};e.prototype.isSearchAtBaseLevel=function(){return!this._sResourceId&&!this._sHistoryVersion&&!this._sCustomOperation};return e});