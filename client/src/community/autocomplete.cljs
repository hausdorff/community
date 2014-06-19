(ns community.autocomplete
  (:require [clojure.string :as str]))

(defprotocol ICursorPosition
  (-cursor-position [this]))

(defprotocol ISetCursorPosition
  (-set-cursor-position [this pos]))

(defprotocol IValue
  (-value [this]))

(defprotocol ISetValue
  (-set-value [this value]))

(extend-type HTMLElement
  ICursorPosition
  (-cursor-position [textarea]
    (.-selectionStart textarea))
  ISetCursorPosition
  (-set-cursor-position [textarea pos]
    (.setSelectionRange textarea pos pos)
    textarea)
  IValue
  (-value [textarea]
    (.-value textarea))
  ISetValue
  (-set-value [textarea value]
    (set! (.-value textarea) value)
    textarea))

(defn starts-with? [s substring]
  (zero? (.indexOf s substring)))

(defn case-insensitive-matches [substring terms {:keys [on]}]
  (let [lower-case-substring (str/lower-case substring)]
    (filter (fn [term]
              (-> (get term on)
                  (str/lower-case)
                  (starts-with? lower-case-substring)))
            terms)))

(defn query-start-index [s pos]
  (loop [i (dec pos)]
    (cond (= i -1) nil
          (= (.charAt s i) "@") (inc i)
          :else (recur (dec i)))))

(defn extract-query [textarea {:keys [marker]}]
  (when-let [start (query-start-index (-value textarea)
                                      (-cursor-position textarea))]
    (.substring (-value textarea) start (-cursor-position textarea))))

(defn possibilities [textarea terms {:keys [on marker]}]
  (when-let [query (extract-query textarea {:marker marker})]
    (case-insensitive-matches query terms {:on on})))

(defn insert [textarea selection {:keys [marker]}]
  (let [selection (str selection " ")
        pos (-cursor-position textarea)
        val (-value textarea)
        start (query-start-index val pos)]
    (-> textarea
        (-set-value (str (.substring val 0 start)
                         selection
                         (.substring val pos)))
        (-set-cursor-position (+ start (count selection))))))