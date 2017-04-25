;; Copyright (c) 2015 Jonathan L. Leonard

(ns speako.test.generators
  (:require [clojure.test.check.generators :as gen :include-macros true]
            [clojure.string :refer [lower-case]]
            [clojure.set]
            [camel-snake-kebab.core :refer [->camelCase ->PascalCase]]))

(defn format
  "Formats a string using goog.string.format."
  [fmt & args]
  (apply goog.string/format fmt args))

(def a-to-z-gen (gen/elements (map char (range 65 (+ 65 26)))))
(def entity-set-gen (gen/set a-to-z-gen {:min-elements 8 :max-elements 25}))
(defn relation-gen [entity-set] (gen/list-distinct (gen/elements entity-set) {:num-elements 2}))

(def entities-and-relations-gen
  (gen/let [entity-set entity-set-gen
            relations (gen/list-distinct-by set (relation-gen entity-set)
                       {:min-elements (int (* (count entity-set) (/ 2 3)))
                        :max-elements (int (* (count entity-set) 3))})]
    {:entities entity-set :relations relations}))

(def multiplicities #{:zero :one :many})
(def multiplicity-gen (gen/elements multiplicities))
(def non-zero-mult-gen (gen/elements (clojure.set/difference multiplicities #{:zero})))
(defn sample-one [generator] (first (gen/sample generator 1)))

(defrecord Multiplicity [entity field multiplicity required?])
(defrecord Cardinality [left right])

(defn relation->cardinalities-gen [[left right]]
  (gen/let [lmult multiplicity-gen
            repeats (gen/choose 1 5)]
    (let [create-card #(Cardinality. (Multiplicity. left (lower-case right) lmult (sample-one gen/boolean))
                                     (Multiplicity. right %1 (sample-one non-zero-mult-gen) (sample-one gen/boolean)))]
      (condp = lmult
        :zero (map create-card (map #(format "%s%s" (lower-case left) %1) (range repeats)))
        [(create-card (lower-case left))]))))

(def entities-and-cardinalities-gen
  (gen/let [{:keys [entities relations]} entities-and-relations-gen
            cardinalities (apply gen/tuple (map relation->cardinalities-gen relations))]
    {:entities entities :relations relations :cardinalities (flatten cardinalities)}))

(defn entity->cardinalities [entity cardinalities]
  (filter #(or (= entity (-> %1 :left :entity)) (= entity (-> %1 :right :entity))) cardinalities))

(defrecord FieldDescriptor [field type multiplicity required?])

(def scalar-types #{"Boolean" "String" "Float" "Timestamp" "Int"})

(defn field-gen [prefix]
  (gen/fmap (partial apply ->FieldDescriptor)
            (gen/tuple (gen/fmap #(format "%s%s" prefix (->PascalCase %1))
                                 (gen/not-empty (gen/fmap clojure.string/join (gen/vector gen/char-alpha))))
                       (gen/elements scalar-types)
                       non-zero-mult-gen
                       gen/boolean)))

(defn cardinality->obj-field-descriptor [entity cardinality]
  (let [grouped (group-by #(-> (%1 1) :entity) cardinality)
        this (first (vals (into {} (grouped entity))))
        other-entity (:entity ((((first (dissoc grouped entity)) 1) 0) 1))]
    (if (not= :zero (:multiplicity this))
      (FieldDescriptor. (:field this) other-entity (:multiplicity this) (:required? this)))))

(defn entity->descriptors-gen [entity cardinalities]
  (let [filtered (entity->cardinalities entity cardinalities) ; Note: O(n^2), can be optimized
        obj-fields (remove nil? (map (partial cardinality->obj-field-descriptor entity) filtered))
        default-field (FieldDescriptor. "id" "ID" :one true)]
    (gen/let [num-fields (gen/choose 2 8)
              plain-fields (apply gen/tuple (repeat num-fields (field-gen (->camelCase entity))))]
      {:entity entity :fields (concat [default-field] plain-fields obj-fields)})))

(def entity-descriptors-gen
  (gen/let [{:keys [entities relations cardinalities]} entities-and-cardinalities-gen
            entity-descriptors (apply gen/tuple (map #(entity->descriptors-gen %1 cardinalities) entities))]
    entity-descriptors))

(defn emit-field [fd]
  (if (#{:one :many} (:multiplicity fd))
    (format "  %s: %s%s%s%s"
            (:field fd)
            (if (= (:multiplicity fd) :many) "[" "")
            (:type fd)
            (if (= (:multiplicity fd) :many) "]" "")
            (if (:required? fd) "!" "")) nil))

(defn emit-type [{:keys [entity fields]}]
  (format "type %s {\n%s\n}"
          entity
          (clojure.string/join "\n" (remove nil? (map emit-field fields)))))

(def schema-str-gen
  (gen/let [entity-descriptors entity-descriptors-gen]
    (clojure.string/join "\n\n" (map emit-type entity-descriptors))))
