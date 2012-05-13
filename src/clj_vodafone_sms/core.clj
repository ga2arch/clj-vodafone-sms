(ns clj-vodafone-sms.core
  (:require [clj-http.client :as client])
  (:use [clojure.string :only (split)])
  (:gen-class))

(def home-url "http://www.vodafone.it/190/trilogy/jsp/home.do")
(def dispatcher-url "http://www.vodafone.it/190/trilogy/jsp/dispatcher.do?ty_key=fdt_invia_sms&tk=9616")
(def dispatcher-url2 "http://www.areaprivati.vodafone.it/190/trilogy/jsp/dispatcher.do?ty_key=fsms_hp&ipage=next")
(def prepare-url "http://www.areaprivati.vodafone.it/190/fsms/prepare.do")
(def captcha-url "http://www.areaprivati.vodafone.it/190/fsms/generateimg.do")
(def send-url "http://www.areaprivati.vodafone.it/190/fsms/send.do")
(def login-url "https://www.vodafone.it/190/trilogy/jsp/login.do")
(def user-agent "Mozilla/5.0 (Windows; U; Windows NT 6.1; it; rv:1.9.2.3) Gecko/20100401 Firefox/3.6.3 (.NET CLR 3.5.30729)")
(def cs (clj-http.cookies/cookie-store))

(declare req)

(defn follow-location [resp]
  (let [loc (get (:headers resp) "location" nil)]
    (if loc
      (follow-location (req :get loc))
      resp)))

(defn req [method url & [opts]]
  (follow-location
   (client/request
    (merge {:method method
            :url url
            :cookie-store cs
            :insecure? true
            :follow-redirects false
            :headers {"User-Agent" user-agent}} opts))))

(defn login [username password]
  (req :get home-url)
  (req :post login-url {:form-params
                         {:username username
                          :password password}}))

(defn dispatcher []
  (req :get dispatcher-url)
  (req :get dispatcher-url2))

(defn prepare-message [num message]
  (req :post prepare-url
       {:form-params
        {:availableChars (str (- 360 (count message)))
         :receiverNumber num
         :message message}}))

(defn save-captcha []
  (let [data (:body (req :get captcha-url {:as :byte-array}))]
    (with-open [out (clojure.java.io/output-stream "captcha.png")]
      (.write out data))))

(defn decode-captcha []
  (let [proc (.. Runtime (getRuntime) (exec "tesseract captcha.png captcha -psm 8"))]
    (.waitFor proc)
    (first (split (slurp "captcha.txt") #"\n"))))

(defn send-form [num message code]
  (req :post send-url
             {:form-params
              {:receiverNumber num
               :message message
               :verifyCode code}}))

(defn show-captcha []
   (.. Runtime (getRuntime) (exec "open /tmp/captcha.png")))

(defn send-sms [num message]
  (try
    (save-captcha)
    (send-form num message (decode-captcha))
    (catch java.net.MalformedURLException e (send-sms num message))))

(defn -main [username password num & msg]
  (let [message (apply str (interpose " " msg))]
    (println (str "Number: " num))
    (println (str "Message: " message))
    (login username password)
    (dispatcher)
    (prepare-message num message)
    (println "Sending..")
    (send-sms num message)
    (println "Sent")))
