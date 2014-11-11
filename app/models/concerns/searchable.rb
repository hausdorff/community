module Searchable
  extend ActiveSupport::Concern

  # default number of results per page
  RESULTS_PER_PAGE = 25

  included do
    include Elasticsearch::Model
    after_commit :upsert_to_search_index!

    # Configure index to searve completion suggestions.
    settings index: { number_of_shards: 1 } do
      mappings dynamic: 'true' do
        indexes :suggest, type: :completion, index_analyzer: :whitespace, search_analyzer: :whitespace, payloads: true
      end
    end
  end

  module ClassMethods
    # Reset search index for the entire model 50 rows at a time
    def reset_search_index!
      self.import force: true, batch_size: 50, transform: lambda { |item| item.to_search_mapping }
    end

    # Search method to query the index for this particular model
    def search(user, search_string, filters, page, opts={})
      __elasticsearch__.search(
        opts.merge({
          query: self.query_dsl(user, search_string, filters),
          highlight: self.highlight_fields,
          from: (page - 1) * RESULTS_PER_PAGE,
          size: RESULTS_PER_PAGE
        })
      )
    end

    # Suggest methods to return suggestions for this particular model
    def suggest(user, search_string)
      suggest_query = { suggestions: { text: search_string.downcase, completion: { field: "suggest" } } }

      suggestions = __elasticsearch__.client.suggest(index: self.table_name, body: suggest_query)["suggestions"]

      suggestions.first["options"].select do |suggestion|
        suggestion["payload"]["required_role_ids"] - user.role_ids == []
      end
    end

    # Override this method to customize the DSL for querying the including model
    def query_dsl(search_string, filters)
      return search_string
    end

    # Override this method to change the fields to highlight when this model is queried
    def highlight_fields
      {}
    end
  end

  # Override this method to change the search mapping for the included model
  def to_search_mapping
    { index: { _id: id, data: __elasticsearch__.as_indexed_json } }
  end

  # Insert/update a single row instance
  def upsert_to_search_index!
    self.class.where(id: id).import transform: lambda { |item| item.to_search_mapping }
  end
end
