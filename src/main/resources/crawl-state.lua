local key = KEYS[1]
local op = ARGV[1]
local now = tonumber(ARGV[2])
local ttl = 86400

local function get(field)
    return redis.call('HGET', key, field)
end

local function terminal(reason, message)
    if not get('stopReason') then
        redis.call('HSET', key, 'stopReason', reason, 'errorMessage', message or '',
            'lastModifiedMillis', now)
    end
end

if op == 'admit' then
    local starts = tonumber(redis.call('GET', key) or '0')
    if starts >= 6 then return {0} end
    if starts == 0 then
        redis.call('SET', key, 1, 'EX', 60)
    else
        redis.call('INCR', key)
    end
    return {1}
end

if op == 'init' then
    if redis.call('EXISTS', key) == 1 then return {0} end
    redis.call('HSET', key,
        'startTimeMillis', now, 'lastModifiedMillis', now,
        'deadline', ARGV[3], 'maxUrls', ARGV[4], 'maxDistance', ARGV[5],
        'numPages', 0, 'distance', 0, 'pending', 0, 'scheduled', 0)
    redis.call('EXPIRE', key, ttl)
    return {1}
end

if redis.call('EXISTS', key) == 0 then return {} end

if not get('stopReason') and now >= tonumber(get('deadline')) then
    terminal('timeout')
end

if op == 'status' then
    return {
        get('distance'), get('startTimeMillis'), get('numPages'),
        get('stopReason') or '', get('errorMessage') or '',
        get('lastModifiedMillis')
    }
elseif op == 'stop' then
    terminal('userInitiated', ARGV[3])
elseif op == 'error' then
    terminal('error', ARGV[3])
elseif op == 'queue' then
    if get('stopReason') then return {0} end
    local field = 'url:' .. ARGV[3]
    if get(field) then return {0} end
    local distance = tonumber(ARGV[4])
    local maxDistance = tonumber(get('maxDistance'))
    if maxDistance >= 0 and distance > maxDistance then
        redis.call('HSET', key, 'depthLimited', 1)
        return {0}
    end
    local maxUrls = tonumber(get('maxUrls'))
    if maxUrls > 0 and tonumber(get('scheduled')) >= maxUrls then return {0} end
    if redis.call('HSETNX', key, field, 'queued') == 0 then return {0} end
    redis.call('HINCRBY', key, 'scheduled', 1)
    redis.call('HINCRBY', key, 'pending', 1)
elseif op == 'claim' then
    if get('stopReason') or get('url:' .. ARGV[3]) ~= 'queued' then return {0} end
    redis.call('HSET', key, 'url:' .. ARGV[3], 'processing')
elseif op == 'finish' or op == 'skip' then
    local field = 'url:' .. ARGV[3]
    if get(field) ~= 'processing' then return {0} end
    redis.call('HSET', key, field, 'done')
    local pending = redis.call('HINCRBY', key, 'pending', -1)
    if not get('stopReason') then
        if op == 'finish' then
            redis.call('HINCRBY', key, 'numPages', 1)
            redis.call('HSET', key, 'distance',
                math.max(tonumber(get('distance')), tonumber(ARGV[4])))
        end
        if pending == 0 then
            local maxUrls = tonumber(get('maxUrls'))
            if maxUrls > 0 and tonumber(get('scheduled')) >= maxUrls then
                terminal('maxUrls')
            elseif get('depthLimited') then
                terminal('maxDistance')
            else
                terminal('completed')
            end
        end
    end
else
    return redis.error_reply('Unknown crawl state operation')
end

redis.call('EXPIRE', key, ttl)
if not get('stopReason') then redis.call('HSET', key, 'lastModifiedMillis', now) end
return {1}