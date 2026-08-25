/**
 * UI5 server middleware that emulates the parts of the epa4all rest-server the frontend needs,
 * so the UI can be developed without a backend:
 *
 *   /fhir/...    -> in-memory mock of the ePA Medication Service (see ../mock/MedicationServiceMock.js)
 *   /webdav/...  -> minimal WebDAV (PROPFIND/GET) over frontend/test-data/webdav/<telematikId>/<KVNR>/local/*.xml
 *   /konnektor/configs, /event/cards, /event/cardterminals, /telematik/id -> Konnektor stubs for the settings dialog
 *                   (one SMC-B per folder in test-data/webdav; the folder name is its Telematik-ID)
 *
 * Activate with the environment variable MOCK_BACKEND=true (see "npm run start:mock").
 */
"use strict";

const fs = require("fs");
const path = require("path");
const { MedicationServiceMock } = require("../mock/MedicationServiceMock");

const WEBDAV_ROOT = path.resolve(__dirname, "../../test-data/webdav");

function readBody(req) {
	return new Promise((resolve, reject) => {
		if (req.body !== undefined) {
			resolve(req.body);
			return;
		}
		const aChunks = [];
		req.on("data", (c) => aChunks.push(c));
		req.on("end", () => resolve(Buffer.concat(aChunks).toString("utf8")));
		req.on("error", reject);
	});
}

function xmlEscape(s) {
	return String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

/**
 * Reads the VSD data of a patient folder and returns the props the patient master list binds to.
 */
function patientProps(sFolder) {
	const sFile = path.join(sFolder, "local", "PersoenlicheVersichertendaten.xml");
	if (!fs.existsSync(sFile)) {
		return {};
	}
	const sXml = new TextDecoder("iso-8859-15").decode(fs.readFileSync(sFile));
	const get = (sTag) => {
		const m = new RegExp("<" + sTag + ">([^<]*)</" + sTag + ">").exec(sXml);
		return m ? m[1] : "";
	};
	const sBirth = get("Geburtsdatum"); // yyyyMMdd
	return {
		firstname: get("Vorname"),
		lastname: get("Nachname"),
		birthday: sBirth.length === 8 ? sBirth.substring(6, 8) + "-" + sBirth.substring(4, 6) + "-" + sBirth.substring(0, 4) : ""
	};
}

function davResponse(sHref, sDisplayName, bCollection, oStat, mExtraProps) {
	const sExtra = Object.keys(mExtraProps || {}).map((k) => "<D:" + k + ">" + xmlEscape(mExtraProps[k]) + "</D:" + k + ">").join("");
	return "<D:response><D:href>" + xmlEscape(sHref) + "</D:href><D:propstat><D:prop>" +
		"<D:creationdate>" + oStat.birthtime.toISOString() + "</D:creationdate>" +
		"<D:getlastmodified>" + oStat.mtime.toUTCString() + "</D:getlastmodified>" +
		(sDisplayName ? "<D:displayname>" + xmlEscape(sDisplayName) + "</D:displayname>" : "") +
		"<D:resourcetype>" + (bCollection ? "<D:collection/>" : "") + "</D:resourcetype>" +
		sExtra +
		"</D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response>";
}

function walk(sDir, sHrefBase, aOut, bDeep) {
	for (const sName of fs.readdirSync(sDir)) {
		const sFull = path.join(sDir, sName);
		const oStat = fs.statSync(sFull);
		aOut.push(davResponse(sHrefBase + "/" + sName, sName, oStat.isDirectory(), oStat));
		if (bDeep && oStat.isDirectory()) {
			walk(sFull, sHrefBase + "/" + sName, aOut, true);
		}
	}
}

function handleWebdav(req, res) {
	const sRelative = decodeURIComponent(req.path.replace(/^\/webdav\/?/, "")).replace(/\/+$/, "");
	const sTarget = path.resolve(WEBDAV_ROOT, sRelative);
	if (!sTarget.startsWith(WEBDAV_ROOT)) {
		res.status(403).end();
		return;
	}
	if (!fs.existsSync(sTarget)) {
		res.status(404).end();
		return;
	}
	const sHrefBase = "http://" + req.headers.host + "/webdav" + (sRelative ? "/" + sRelative : "");
	if (req.method === "PROPFIND") {
		const sDepth = String(req.headers.depth || "1").toLowerCase();
		const oStat = fs.statSync(sTarget);
		const aResponses = [davResponse(sHrefBase, path.basename(sTarget), oStat.isDirectory(), oStat)];
		const aSegments = sRelative.split("/").filter(Boolean);
		if (oStat.isDirectory() && sDepth !== "0") {
			if (aSegments.length === 1) {
				// telematik id folder: list patients with the props the master list needs
				const aPatients = fs.readdirSync(sTarget).filter((n) => fs.statSync(path.join(sTarget, n)).isDirectory());
				const iOffset = parseInt(req.headers["x-offset"] || "0", 10);
				const iLimit = parseInt(req.headers["x-limit"] || "20", 10);
				res.set("X-Total-Count", String(aPatients.length));
				aPatients.slice(iOffset, iOffset + iLimit).forEach((sKvnr) => {
					const sFolder = path.join(sTarget, sKvnr);
					aResponses.push(davResponse(sHrefBase + "/" + sKvnr, sKvnr, true, fs.statSync(sFolder), patientProps(sFolder)));
				});
			} else {
				walk(sTarget, sHrefBase, aResponses, sDepth === "infinity");
			}
		}
		res.status(207).type("application/xml").send("<?xml version=\"1.0\" encoding=\"utf-8\"?><D:multistatus xmlns:D=\"DAV:\">" + aResponses.join("") + "</D:multistatus>");
		return;
	}
	if (req.method === "GET" && fs.statSync(sTarget).isFile()) {
		res.status(200).type("application/xml").send(fs.readFileSync(sTarget));
		return;
	}
	res.status(405).end();
}

/**
 * Konnektor endpoints used by the settings dialog: one SMC-B test card per folder in test-data/webdav,
 * the folder name is the Telematik-ID that is returned by /telematik/id.
 */
function mockCards() {
	return fs.readdirSync(WEBDAV_ROOT)
		.filter((n) => fs.statSync(path.join(WEBDAV_ROOT, n)).isDirectory())
		.map((sTelematikId, i) => ({
			telematikId: sTelematikId,
			iccsn: "8027688311000" + String(i + 1).padStart(7, "0"),
			cardHandle: "SMC-B-" + (i + 1),
			holder: "Mock SMC-B " + sTelematikId
		}));
}

function handleKonnektor(req, res) {
	if (req.path === "/konnektor/configs") {
		res.status(200).type("application/xml").send("<?xml version=\"1.0\" encoding=\"utf-8\"?><collection><KonnektorConfig><connectorBaseURL>https://mock-konnektor.local</connectorBaseURL></KonnektorConfig></collection>");
		return true;
	}
	if (req.path === "/event/cards") {
		const sCards = mockCards().map((c) =>
			"<ns5:Card><ns3:CardHandle>" + xmlEscape(c.cardHandle) + "</ns3:CardHandle><ns4:CardType>SMC-B</ns4:CardType><ns4:Iccsn>" + c.iccsn + "</ns4:Iccsn>" +
			"<ns4:CtId>T_MOCK</ns4:CtId><ns4:SlotId>1</ns4:SlotId><ns5:CardHolderName>" + xmlEscape(c.holder) + "</ns5:CardHolderName></ns5:Card>").join("");
		res.status(200).type("application/xml").send("<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
			"<ns5:GetCardsResponse xmlns:ns3=\"http://ws.gematik.de/conn/ConnectorCommon/v5.0\" xmlns:ns4=\"http://ws.gematik.de/conn/CardServiceCommon/v2.0\" xmlns:ns5=\"http://ws.gematik.de/conn/CardService/v8.1\">" +
			"<ns5:Cards>" + sCards + "</ns5:Cards></ns5:GetCardsResponse>");
		return true;
	}
	if (req.path === "/event/cardterminals") {
		res.status(200).type("application/xml").send("<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
			"<ns8:GetCardTerminalsResponse xmlns:ns4=\"http://ws.gematik.de/conn/CardServiceCommon/v2.0\" xmlns:ns8=\"http://ws.gematik.de/conn/CardTerminalInfo/v8.0\">" +
			"<ns8:CardTerminals><ns8:CardTerminal><ns4:CtId>T_MOCK</ns4:CtId><ns8:Name>Mock Kartenterminal</ns8:Name><ns8:IPV4Address>127.0.0.1</ns8:IPV4Address></ns8:CardTerminal></ns8:CardTerminals></ns8:GetCardTerminalsResponse>");
		return true;
	}
	if (req.path === "/telematik/id") {
		const oCard = mockCards().find((c) => c.iccsn === req.query.iccsn) || mockCards()[0];
		if (!oCard) {
			res.status(404).end();
			return true;
		}
		res.status(200).type("text/plain").send(oCard.telematikId);
		return true;
	}
	return false;
}

module.exports = function ({ log }) {
	const bEnabled = /^(1|true|yes)$/i.test(process.env.MOCK_BACKEND || "");
	const fnLog = log && log.info ? log.info.bind(log) : console.log;
	if (!bEnabled) {
		fnLog("mockBackend: disabled (set MOCK_BACKEND=true or use 'npm run start:mock' to serve /fhir and /webdav from mocks)");
		return (req, res, next) => next();
	}
	const oMedicationService = new MedicationServiceMock();
	fnLog("mockBackend: enabled - /fhir/* served by MedicationServiceMock, /webdav/* served from " + WEBDAV_ROOT);

	return async function (req, res, next) {
		try {
			if (handleKonnektor(req, res)) {
				return;
			}
			if (req.path.startsWith("/webdav")) {
				handleWebdav(req, res);
				return;
			}
			if (req.path.startsWith("/fhir")) {
				const sPath = req.path.replace(/^\/fhir\/?/, "");
				const sBody = ["POST", "PUT"].includes(req.method) ? await readBody(req) : undefined;
				let oBody;
				if (sBody) {
					try {
						oBody = typeof sBody === "string" ? JSON.parse(sBody) : sBody;
					} catch (e) {
						oBody = undefined;
					}
				}
				const oResult = oMedicationService.handle(req.method, decodeURIComponent(sPath), req.query || {}, req.headers, oBody);
				res.status(oResult.status).type("application/fhir+json").send(JSON.stringify(oResult.body));
				return;
			}
			next();
		} catch (e) {
			next(e);
		}
	};
};
